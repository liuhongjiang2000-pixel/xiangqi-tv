package com.liuhong.xiangqi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.liuhong.xiangqi.engine.Board;
import com.liuhong.xiangqi.engine.Search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 单机中国象棋：开场菜单（当前强度 + 战绩统计 + 开始游戏）+ 棋盘对局界面。
 * AI「刘洪江」强度自动调整：玩家每赢一局强度升一档，每输一局降一档；
 * 对局中途退出（切后台/回到桌面）自动判负并计入战绩；
 * AI 获胜后随机嘲讽，落败后随机嘴硬。
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
    private static final int[] DEPTHS = {1, 1, 2, 2, 3, 3, 4};
    private static final int[] RAND_MARGINS = {200, 120, 120, 60, 60, 0, 0};
    private static final int MAX_STRENGTH = STRENGTH_NAMES.length - 1;
    private static final int DEFAULT_STRENGTH = 2;
    private static final long AI_TIME_MS = 2500;
    private static final String PREFS = "xiangqi_stats";

    private Board board;
    private BoardView boardView;
    private TextView statusView;
    private Button btnNew, btnUndo, btnAiFirst;

    private int humanSide = 1;
    private boolean aiFirst = false;
    private int strength = DEFAULT_STRENGTH;
    private boolean gameOver = false;
    private boolean gameStarted = false;
    private boolean thinking = false;

    // 战绩统计
    private int wins, losses, draws;
    private SharedPreferences prefs;

    /** 走子记录：from(8bit) | to(8bit) | captured(8bit 带符号)。 */
    private final Deque<int[]> history = new ArrayDeque<int[]>();
    /** 局面哈希历史（含初始局面），用于三次重复作和。 */
    private final java.util.List<Long> posHashes = new java.util.ArrayList<Long>();

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final java.util.Random random = new java.util.Random();
    private ToneGenerator tones;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 开场菜单：当前强度 / 战绩统计 / 开始游戏
        setContentView(R.layout.activity_menu);

        tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadStats();

        Button btnMenuStart = findViewById(R.id.btn_menu_start);
        btnMenuStart.setOnClickListener(v -> startGame());
        btnMenuStart.requestFocus();
        updateMenuText();
    }

    private void loadStats() {
        wins = prefs.getInt("wins", 0);
        losses = prefs.getInt("losses", 0);
        draws = prefs.getInt("draws", 0);
        strength = prefs.getInt("strength", DEFAULT_STRENGTH);
    }

    private void saveStats() {
        prefs.edit()
                .putInt("wins", wins)
                .putInt("losses", losses)
                .putInt("draws", draws)
                .putInt("strength", strength)
                .apply();
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

    /** 进入棋盘界面并开始新对局。 */
    private void startGame() {
        setContentView(R.layout.activity_game);

        boardView = findViewById(R.id.board);
        statusView = findViewById(R.id.status);
        btnNew = findViewById(R.id.btn_new);
        btnUndo = findViewById(R.id.btn_undo);
        btnAiFirst = findViewById(R.id.btn_aifirst);

        board = new Board();
        boardView.setOnMoveListener(this);
        boardView.setFocusDownTarget(btnNew);

        btnNew.setOnClickListener(v -> newGame());
        btnUndo.setOnClickListener(v -> undo());
        btnAiFirst.setOnClickListener(v -> {
            aiFirst = !aiFirst;
            updateAiFirstButton();
            newGame();
        });

        updateAiFirstButton();
        newGame();
    }

    private void newGame() {
        board.reset();
        humanSide = aiFirst ? -1 : 1;
        gameOver = false;
        gameStarted = true;
        history.clear();
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
        history.push(new int[]{from, to, captured});
        board.make(move);
        posHashes.add(board.hash());
        boardView.setLastMove(move);
        if (captured != Board.EMPTY) playCaptureSound();
        else beep(false);
        updateStatus();
        checkRepetition();
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

    /** 终局统一处理：提示语、战绩、强度、禁输入。 */
    private void endGame(String message, int result) {
        if (gameOver) return;
        gameOver = true;
        gameStarted = false;
        recordResult(result);
        boardView.setInputEnabled(false);
        String tail;
        if (result > 0) {
            // 玩家获胜：刘洪江嘴硬一句
            tail = AI_NAME + " 升至「" + STRENGTH_NAMES[strength] + "」嘴上不服："
                    + GRUMBLES[random.nextInt(GRUMBLES.length)];
        } else if (result < 0) {
            // AI 获胜：报名号并随机嘲讽一句
            tail = AI_NAME + "「" + STRENGTH_NAMES[strength] + "」："
                    + TAUNTS[random.nextInt(TAUNTS.length)];
        } else {
            tail = "强度不变";
        }
        statusView.setText(message + " " + tail + "，按「新对局」再来一局");
        beep(true);
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
        // 已终局（含中途退出判负）的棋局不允许悔棋复活
        if (!gameStarted || thinking || history.isEmpty()) return;
        popOne();
        // 人机模式下回退到玩家回合
        if (board.side != humanSide && !history.isEmpty()) {
            popOne();
        }
        gameOver = false;
        boardView.setLastMove(-1);
        boardView.setGame(board, humanSide);
        updateStatus();
        if (board.side != humanSide) {
            aiMove();
        } else {
            boardView.setInputEnabled(true);
            boardView.requestFocus();
        }
    }

    private void popOne() {
        int[] rec = history.pop();
        board.sq[rec[0]] = board.sq[rec[1]];
        board.sq[rec[1]] = rec[2];
        board.side = -board.side;
        if (!posHashes.isEmpty()) posHashes.remove(posHashes.size() - 1);
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

    private void updateAiFirstButton() {
        btnAiFirst.setText(aiFirst ? AI_NAME + "执红先行：开" : AI_NAME + "执红先行：关");
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
        super.onDestroy();
    }
}
