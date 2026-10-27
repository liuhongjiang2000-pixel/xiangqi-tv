package com.liuhong.xiangqi;

import android.app.Activity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.View;
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
 * 单机中国象棋主界面：棋盘 + 状态栏 + 功能按钮。
 * 人人对局 / 人机对局，多档难度，悔棋、AI 先手。
 */
public class GameActivity extends Activity implements BoardView.OnMoveListener {

    private static final int[] DEPTHS = {1, 2, 3, 4};
    private static final int[] RAND_MARGINS = {120, 60, 0, 0};
    private static final String[] LEVEL_NAMES = {"入门", "初级", "中级", "高级"};
    private static final long AI_TIME_MS = 2500;

    private Board board;
    private BoardView boardView;
    private TextView statusView;
    private Button btnNew, btnUndo, btnLevel, btnAiFirst;

    private int humanSide = 1;
    private boolean aiFirst = false;
    private int level = 1; // 0..3
    private boolean gameOver = false;
    private boolean thinking = false;

    /** 走子记录：from(8bit) | to(8bit) | captured(8bit 带符号)。 */
    private final Deque<int[]> history = new ArrayDeque<int[]>();
    /** 局面哈希历史（含初始局面），用于三次重复作和。 */
    private final java.util.List<Long> posHashes = new java.util.ArrayList<Long>();

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private ToneGenerator tones;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        boardView = findViewById(R.id.board);
        statusView = findViewById(R.id.status);
        btnNew = findViewById(R.id.btn_new);
        btnUndo = findViewById(R.id.btn_undo);
        btnLevel = findViewById(R.id.btn_level);
        btnAiFirst = findViewById(R.id.btn_aifirst);

        tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);

        board = new Board();
        boardView.setOnMoveListener(this);
        boardView.setFocusDownTarget(btnNew);

        btnNew.setOnClickListener(v -> newGame());
        btnUndo.setOnClickListener(v -> undo());
        btnLevel.setOnClickListener(v -> {
            level = (level + 1) % LEVEL_NAMES.length;
            updateLevelButton();
            toast("难度：" + LEVEL_NAMES[level]);
        });
        btnAiFirst.setOnClickListener(v -> {
            aiFirst = !aiFirst;
            updateAiFirstButton();
            newGame();
        });

        updateLevelButton();
        updateAiFirstButton();
        newGame();
    }

    private void newGame() {
        board.reset();
        humanSide = aiFirst ? -1 : 1;
        gameOver = false;
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
            int move = search.findBest(board, DEPTHS[level], RAND_MARGINS[level]);
            runOnUiThread(() -> {
                thinking = false;
                if (isFinishing()) return;
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
        beep(captured != Board.EMPTY);
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
            gameOver = true;
            boardView.setInputEnabled(false);
            statusView.setText("双方不变作和！按「新对局」再来一局");
            beep(true);
        }
    }

    private boolean checkEnd() {
        if (gameOver) return true; // 已和棋（重复局面）等终局状态
        int status = board.status();
        if (status == Board.ONGOING) {
            boardView.setInputEnabled(board.side == humanSide);
            if (board.side == humanSide) boardView.requestFocus();
            return false;
        }
        gameOver = true;
        boardView.setInputEnabled(false);
        boolean redWin = status == Board.RED_WIN;
        String winner = redWin ? "红方" : "黑方";
        String reason = board.inCheck() ? "将死" : "困毙";
        statusView.setText(winner + "胜（" + reason + "）！按「新对局」再来一局");
        beep(true);
        return true;
    }

    private void undo() {
        if (thinking || history.isEmpty()) return;
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
            statusView.setText((board.side > 0 ? "红方" : "黑方") + "（电脑）思考中…");
        } else {
            String who = board.side > 0 ? "红方" : "黑方";
            String owner = board.side == humanSide ? "（你）" : "（电脑）";
            String check = board.inCheck() ? " · 将军！" : "";
            statusView.setText("轮到 " + who + owner + " 走棋" + check);
        }
    }

    private void updateLevelButton() {
        btnLevel.setText("难度：" + LEVEL_NAMES[level]);
    }

    private void updateAiFirstButton() {
        btnAiFirst.setText(aiFirst ? "AI 执红先行：开" : "AI 执红先行：关");
    }

    private void beep(boolean strong) {
        try {
            tones.startTone(strong
                    ? ToneGenerator.TONE_PROP_ACK
                    : ToneGenerator.TONE_PROP_BEEP, 70);
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
