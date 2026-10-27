package com.liuhong.xiangqi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.liuhong.xiangqi.engine.Board;
import com.liuhong.xiangqi.engine.Search;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 单机中国象棋。
 * 流程：主菜单（强度/战绩/语音/AI 先手/开始游戏）→ 棋盘对局（精简界面）
 * → 结算界面（胜负和 + 刘洪江台词 + 再来一局/返回主菜单）。
 * AI「刘洪江」强度自动调整；中途退出自动判负；支持中文语音播报。
 */
public class GameActivity extends Activity implements BoardView.OnMoveListener {

    private static final String[] STRENGTH_NAMES = {"菜鸟", "新手", "初级", "中级", "高级", "大师", "特级大师"};
    private static final String AI_NAME = "刘洪江";
    private static final String[] TAUNTS = {
            "就这点本事？回去再练十年吧！",
            "将军！承让承让～",
            "这局我又赢了，啧啧。",
            "棋力还行，可惜你的对手是我。",
            "服不服？不服再来！",
            "这一步我三手之前就算到了。",
            "年轻人，棋要一步一步下。",
    };
    private static final String[] GRUMBLES = {
            "这盘让你，下盘我认真。",
            "刚才我漏算了……不算，重来！",
            "你是蒙的吧？肯定是蒙的。",
            "哼，赢了我你也别得意。",
            "让了你车马炮，你懂的。",
            "这局状态不好，喝口水再战。",
            "年纪轻轻，下手这么狠……",
    };
    private static final int[] DEPTHS = {1, 2, 3, 3, 4, 4, 5};
    private static final int[] RAND_MARGINS = {250, 160, 120, 60, 30, 0, 0};
    private static final int MAX_STRENGTH = STRENGTH_NAMES.length - 1;
    private static final int DEFAULT_STRENGTH = 2;
    private static final long AI_TIME_MS = 2500;
    private static final String PREFS = "xiangqi_stats";

    private Board board;
    private BoardView boardView;
    private TextView statusView;
    private Button btnNew;

    private int humanSide = 1;
    private boolean aiFirst = false;
    private int strength = DEFAULT_STRENGTH;
    private boolean gameOver = false;
    private boolean gameStarted = false;
    private boolean thinking = false;
    private boolean voiceOn = true;

    // 结算界面缓存
    private int lastResult = 0;
    private String lastQuote = "";

    // 战绩统计
    private int wins, losses, draws;
    private SharedPreferences prefs;

    /** 局面哈希历史（含初始局面），用于三次重复作和。 */
    private final java.util.List<Long> posHashes = new java.util.ArrayList<Long>();

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final java.util.Random random = new java.util.Random();
    private ToneGenerator tones;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadStats();

        tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
        initTts();

        showMenu();
    }

    // ---------- 语音 ----------

    private void initTts() {
        try {
            tts = new TextToSpeech(this, status -> {
                runOnUiThread(GameActivity.this::updateMenuText);
                if (status != TextToSpeech.SUCCESS) {
                    tts = null;
                    return;
                }
                int r = tts.setLanguage(Locale.CHINESE);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.shutdown();
                    tts = null;
                } else {
                    tts.setSpeechRate(0.95f);
                }
                runOnUiThread(GameActivity.this::updateMenuText);
            });
        } catch (Exception e) {
            tts = null;
        }
    }

    /** 语音播报（静默降级：无 TTS 或关闭时不发声）。 */
    private void speak(String text) {
        if (!voiceOn || tts == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "xiangqi");
        } catch (Exception ignored) {
        }
    }

    /** 追加播报（不打断前面的语音）。 */
    private void speakAdd(String text) {
        if (!voiceOn || tts == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "xiangqi");
        } catch (Exception ignored) {
        }
    }

    // ---------- 主菜单 ----------

    private void showMenu() {
        setContentView(R.layout.activity_menu);

        Button btnStart = findViewById(R.id.btn_menu_start);
        Button btnAiFirst = findViewById(R.id.btn_menu_aifirst);
        Button btnVoice = findViewById(R.id.btn_menu_voice);

        btnStart.setOnClickListener(v -> startGame());
        btnAiFirst.setOnClickListener(v -> {
            aiFirst = !aiFirst;
            saveStats();
            updateMenuText();
        });
        btnVoice.setOnClickListener(v -> {
            if (tts == null) {
                toast("电视没有安装语音引擎，无法使用语音");
                return;
            }
            voiceOn = !voiceOn;
            saveStats();
            updateMenuText();
            if (voiceOn) speak("语音已开启");
        });

        btnStart.requestFocus();
        updateMenuText();
    }

    private void updateMenuText() {
        TextView tvStrength = findViewById(R.id.tv_strength);
        TextView tvStats = findViewById(R.id.tv_stats);
        if (tvStrength == null || tvStats == null) return;
        tvStrength.setText("当前强度：" + STRENGTH_NAMES[strength]);
        int games = wins + losses + draws;
        String rate = games == 0 ? "—" : (wins * 100 / games) + "%";
        tvStats.setText("战绩：" + games + " 场 · 胜 " + wins + " / 负 " + losses
                + " / 和 " + draws + " · 胜率 " + rate
                + "\n（获胜 " + AI_NAME + " 升档，落败降档）");
        Button btnAiFirst = findViewById(R.id.btn_menu_aifirst);
        Button btnVoice = findViewById(R.id.btn_menu_voice);
        if (btnAiFirst != null) {
            btnAiFirst.setText(aiFirst ? AI_NAME + "执红先行：开" : AI_NAME + "执红先行：关");
        }
        if (btnVoice != null) {
            btnVoice.setText(tts == null ? "语音：不可用" : (voiceOn ? "语音：开" : "语音：关"));
        }
    }

    // ---------- 对局 ----------

    private void startGame() {
        setContentView(R.layout.activity_game);

        boardView = findViewById(R.id.board);
        statusView = findViewById(R.id.status);
        btnNew = findViewById(R.id.btn_new);
        if (boardView == null || statusView == null || btnNew == null) {
            // 个别电视系统会复用旧的资源缓存，导致按 id 找不到布局里的控件；
            // 此时退化为纯代码构建对局界面，保证一定能进入游戏。
            buildGameUiFallback();
        }

        board = new Board();
        boardView.setOnMoveListener(this);
        boardView.setFocusDownTarget(btnNew);

        btnNew.setOnClickListener(v -> newGame());

        newGame();
    }

    /** 资源缓存异常时兜底：用代码搭出对局界面（状态栏 + 棋盘 + 新游戏按钮）。 */
    private void buildGameUiFallback() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density + 0.5f);

        statusView = new TextView(this);
        statusView.setTextSize(18);
        statusView.setTextColor(Color.WHITE);
        statusView.setPadding(pad, pad, pad, pad);

        boardView = new BoardView(this);

        btnNew = new Button(this);
        btnNew.setText("新游戏");

        root.addView(statusView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(boardView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(btnNew, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void newGame() {
        board.reset();
        humanSide = aiFirst ? -1 : 1;
        gameOver = false;
        gameStarted = true;
        posHashes.clear();
        posHashes.add(board.hash());
        boardView.setGame(board, humanSide);
        boardView.setLastMove(-1);
        updateStatus();
        if (board.side != humanSide) {
            aiMove();
        } else {
            boardView.setInputEnabled(true);
            boardView.requestFocus();
        }
    }

    @Override
    public void onHumanMove(int move) {
        if (gameOver || thinking) return;
        applyMove(move);
        if (gameOver || checkEnd()) return;
        aiMove();
    }

    private void aiMove() {
        thinking = true;
        boardView.setInputEnabled(false);
        updateStatus();
        exec.execute(() -> {
            Search search = new Search(AI_TIME_MS);
            int move = search.findBest(board, DEPTHS[strength], RAND_MARGINS[strength]);
            runOnUiThread(() -> {
                thinking = false;
                if (isFinishing() || gameOver) return;
                if (move >= 0) {
                    applyMove(move);
                    checkEnd();
                } else {
                    updateStatus();
                }
            });
        });
    }

    private void applyMove(int move) {
        int from = move >>> 8;
        int to = move & 0xff;
        int captured = board.sq[to];
        board.make(move);
        posHashes.add(board.hash());
        boardView.setLastMove(move);
        if (captured != Board.EMPTY) {
            playCaptureSound();
            speak("吃" + pieceName(captured));
        } else {
            beep(false);
        }
        if (board.inCheck()) {
            speakAdd("将军");
        }
        updateStatus();
        checkRepetition();
    }

    /** 棋子中文名（用于吃子播报）。 */
    private String pieceName(int p) {
        int t = Board.type(p);
        boolean red = Board.color(p) > 0;
        switch (t) {
            case Board.KING: return red ? "帅" : "将";
            case Board.ADVISOR: return red ? "仕" : "士";
            case Board.ELEPHANT: return red ? "相" : "象";
            case Board.HORSE: return "马";
            case Board.ROOK: return "车";
            case Board.CANNON: return "炮";
            case Board.PAWN: return red ? "兵" : "卒";
            default: return "";
        }
    }

    /** 同一局面（含走子方）出现三次判和。 */
    private void checkRepetition() {
        if (gameOver) return;
        long h = board.hash();
        int count = 0;
        for (long x : posHashes) {
            if (x == h) count++;
        }
        if (count >= 3) {
            endGame("双方不变作和！", 0);
        }
    }

    private boolean checkEnd() {
        if (gameOver) return true; // 已终局
        int status = board.status();
        if (status == Board.ONGOING) {
            boardView.setInputEnabled(board.side == humanSide);
            if (board.side == humanSide) boardView.requestFocus();
            return false;
        }
        boolean redWin = status == Board.RED_WIN;
        String winner = redWin ? "红方" : "黑方";
        String reason = board.inCheck() ? "将死" : "困毙";
        boolean humanWin = (status == Board.RED_WIN) == (humanSide > 0);
        endGame(winner + "胜（" + reason + "）！", humanWin ? 1 : -1);
        return true;
    }

    /** 终局统一处理：战绩、强度、状态栏提示，稍后弹出结算界面。 */
    private void endGame(String message, int result) {
        if (gameOver) return;
        gameOver = true;
        gameStarted = false;
        recordResult(result);
        boardView.setInputEnabled(false);
        lastResult = result;
        if (result > 0) {
            lastQuote = GRUMBLES[random.nextInt(GRUMBLES.length)];
            statusView.setText(message + " " + AI_NAME + " 嘴上不服，正在结算…");
            speak("你赢了！" + lastQuote);
        } else if (result < 0) {
            lastQuote = TAUNTS[random.nextInt(TAUNTS.length)];
            statusView.setText(message + " " + AI_NAME + " 正在嘲讽，稍后结算…");
            speak("你输了。" + lastQuote);
        } else {
            lastQuote = "";
            statusView.setText(message + " 正在结算…");
            speak("和棋");
        }
        beep(true);
        // 稍等片刻再切结算界面，让玩家看清最后一手；
        // 若玩家已开新局（gameOver 被复位）则不再弹出
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    if (gameOver && !isFinishing()) showResult();
                }, 2000);
    }

    // ---------- 结算界面 ----------

    private void showResult() {
        if (isFinishing()) return;
        setContentView(R.layout.activity_result);

        TextView tvResult = findViewById(R.id.tv_result);
        TextView tvQuote = findViewById(R.id.tv_quote);
        TextView tvStats = findViewById(R.id.tv_result_stats);

        String title;
        int color;
        if (lastResult > 0) {
            title = "你赢了！";
            color = Color.parseColor("#66BB6A");
        } else if (lastResult < 0) {
            title = "你输了…";
            color = Color.parseColor("#EF5350");
        } else {
            title = "和棋";
            color = Color.parseColor("#FFB300");
        }
        tvResult.setText(title);
        tvResult.setTextColor(color);
        if (lastQuote.isEmpty()) {
            tvQuote.setText("双方势均力敌，不分胜负。");
        } else {
            tvQuote.setText(AI_NAME + "：" + lastQuote);
        }
        int games = wins + losses + draws;
        String rate = games == 0 ? "—" : (wins * 100 / games) + "%";
        String strengthLine;
        if (lastResult > 0) {
            strengthLine = AI_NAME + " 已升至「" + STRENGTH_NAMES[strength] + "」，下局更难";
        } else if (lastResult < 0) {
            strengthLine = AI_NAME + " 已降至「" + STRENGTH_NAMES[strength] + "」，下局稍易";
        } else {
            strengthLine = "当前强度「" + STRENGTH_NAMES[strength] + "」不变";
        }
        tvStats.setText("战绩 " + games + " 场：胜 " + wins + " / 负 " + losses
                + " / 和 " + draws + " · 胜率 " + rate + "\n" + strengthLine);

        Button btnAgain = findViewById(R.id.btn_again);
        Button btnToMenu = findViewById(R.id.btn_tomenu);
        btnAgain.setOnClickListener(v -> startGame());
        btnToMenu.setOnClickListener(v -> showMenu());
        btnAgain.requestFocus();
    }

    // ---------- 战绩 ----------

    private void loadStats() {
        wins = prefs.getInt("wins", 0);
        losses = prefs.getInt("losses", 0);
        draws = prefs.getInt("draws", 0);
        strength = prefs.getInt("strength", DEFAULT_STRENGTH);
        aiFirst = prefs.getBoolean("aifirst", false);
        voiceOn = prefs.getBoolean("voice", true);
    }

    private void saveStats() {
        prefs.edit()
                .putInt("wins", wins)
                .putInt("losses", losses)
                .putInt("draws", draws)
                .putInt("strength", strength)
                .putBoolean("aifirst", aiFirst)
                .putBoolean("voice", voiceOn)
                .apply();
    }

    /** 记录一局结果并自动调整 AI 强度：1 胜 / 0 和 / -1 负。 */
    private void recordResult(int result) {
        if (result > 0) {
            wins++;
            strength = Math.min(strength + 1, MAX_STRENGTH);
        } else if (result < 0) {
            losses++;
            strength = Math.max(strength - 1, 0);
        } else {
            draws++;
        }
        saveStats();
    }

    /** 对局中途退出（切后台/回桌面）自动判负。 */
    @Override
    protected void onPause() {
        super.onPause();
        if (gameStarted && !gameOver) {
            recordResult(-1);
            gameOver = true;
            gameStarted = false;
            thinking = false;
            if (boardView != null) boardView.setInputEnabled(false);
            if (statusView != null) {
                statusView.setText("中途退出，本局判负。按「新对局」重新开始");
            }
        }
    }

    private void undo() {
        // 悔棋功能已按需求取消，保留空实现防止旧引用
    }

    private void popOne() {
    }

    private void updateStatus() {
        if (gameOver) return;
        if (thinking) {
            statusView.setText((board.side > 0 ? "红方" : "黑方") + "（" + AI_NAME + "）思考中…");
        } else {
            String who = board.side > 0 ? "红方" : "黑方";
            String owner = board.side == humanSide ? "（你）" : "（" + AI_NAME + "）";
            String check = board.inCheck() ? " · 将军！" : "";
            statusView.setText("轮到 " + who + owner + " 走棋" + check);
        }
    }

    private void beep(boolean strong) {
        try {
            tones.startTone(strong
                    ? ToneGenerator.TONE_PROP_ACK
                    : ToneGenerator.TONE_PROP_BEEP, 70);
        } catch (Exception ignored) {
        }
    }

    /** 吃子音效：短促双音，模拟"打击感"。 */
    private void playCaptureSound() {
        try {
            tones.startTone(ToneGenerator.TONE_PROP_ACK, 90);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        try {
                            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
                        } catch (Exception ignored) {
                        }
                    }, 110);
        } catch (Exception ignored) {
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        if (tones != null) tones.release();
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
