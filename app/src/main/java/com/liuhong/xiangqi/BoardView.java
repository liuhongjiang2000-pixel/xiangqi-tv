package com.liuhong.xiangqi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.liuhong.xiangqi.engine.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * 自绘棋盘：Canvas 绘制棋盘、棋子、选子与光标。
 * 支持触屏点按与遥控器 D-pad（方向键移动光标，确认键选子/落子）。
 */
public class BoardView extends View {

    public interface OnMoveListener {
        void onHumanMove(int move);
    }

    private Board board;
    private int humanSide = 1;
    private boolean inputEnabled = true;

    private int selected = -1;
    private List<Integer> targets = new ArrayList<Integer>();
    private int lastMove = -1;
    private int cursor = Board.idx(9, 4);

    private OnMoveListener listener;
    /** 光标在棋盘最底行时按"下"键转移焦点的目标（底部功能按钮栏）。 */
    private View focusDownTarget;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint riverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float ox, oy, cell; // 棋盘左上角偏移与格距

    public BoardView(Context context) {
        this(context, null);
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);
        bgPaint.setColor(0xFFE8C48A);
        linePaint.setColor(0xFF6D4C2F);
        linePaint.setStrokeWidth(2.5f);
        linePaint.setStyle(Paint.Style.STROKE);
        pieceFillPaint.setColor(0xFFFDF3D8);
        pieceStrokePaint.setColor(0xFF6D4C2F);
        pieceStrokePaint.setStyle(Paint.Style.STROKE);
        pieceStrokePaint.setStrokeWidth(3f);
        redTextPaint.setColor(0xFFC62828);
        redTextPaint.setTextAlign(Paint.Align.CENTER);
        blackTextPaint.setColor(0xFF263238);
        blackTextPaint.setTextAlign(Paint.Align.CENTER);
        selPaint.setColor(0x6639A0FF);
        selPaint.setStyle(Paint.Style.FILL);
        cursorPaint.setColor(0xFFFFB300);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(4f);
        dotPaint.setColor(0x7738A169);
        lastPaint.setColor(0x44FFFFFF);
        riverPaint.setColor(0xFF6D4C2F);
        riverPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(Board board, int humanSide) {
        this.board = board;
        this.humanSide = humanSide;
        this.selected = -1;
        this.targets.clear();
        this.lastMove = -1;
        this.cursor = humanSide > 0 ? Board.idx(9, 4) : Board.idx(0, 4);
        invalidate();
    }

    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
        if (!enabled) {
            selected = -1;
            targets.clear();
        }
        invalidate();
    }

    public void setLastMove(int move) {
        this.lastMove = move;
        invalidate();
    }

    public void setOnMoveListener(OnMoveListener l) {
        this.listener = l;
    }

    /** 设置光标在棋盘最底行继续按"下"键时接收焦点的视图（底部按钮栏）。 */
    public void setFocusDownTarget(View v) {
        this.focusDownTarget = v;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cell = Math.min(w / 9.4f, h / 10.4f);
        ox = (w - cell * 8) / 2f;
        oy = (h - cell * 9) / 2f;
        float textSize = cell * 0.62f;
        redTextPaint.setTextSize(textSize);
        blackTextPaint.setTextSize(textSize);
        riverPaint.setTextSize(cell * 0.55f);
    }

    private float px(int col) {
        return ox + col * cell;
    }

    private float py(int row) {
        return oy + row * cell;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // 外框
        float pad = linePaint.getStrokeWidth();
        canvas.drawRect(ox - pad, oy - pad, px(8) + pad, py(9) + pad, linePaint);

        // 横线
        for (int r = 1; r < 9; r++) {
            canvas.drawLine(ox, py(r), px(8), py(r), linePaint);
        }
        // 竖线（河道断开）
        for (int c = 0; c < 9; c++) {
            if (c == 0 || c == 8) {
                canvas.drawLine(px(c), oy, px(c), py(9), linePaint);
            } else {
                canvas.drawLine(px(c), oy, px(c), py(4), linePaint);
                canvas.drawLine(px(c), py(5), px(c), py(9), linePaint);
            }
        }
        // 九宫斜线
        canvas.drawLine(px(3), py(0), px(5), py(2), linePaint);
        canvas.drawLine(px(5), py(0), px(3), py(2), linePaint);
        canvas.drawLine(px(3), py(7), px(5), py(9), linePaint);
        canvas.drawLine(px(5), py(7), px(3), py(9), linePaint);
        // 河界文字
        canvas.drawText("楚 河", px(2), py(4) + cell * 0.72f, riverPaint);
        canvas.drawText("汉 界", px(6), py(4) + cell * 0.72f, riverPaint);

        // 上一步提示
        if (lastMove >= 0) {
            int f = lastMove >>> 8, t = lastMove & 0xff;
            drawSquare(canvas, f, lastPaint);
            drawSquare(canvas, t, lastPaint);
        }
        // 选中
        if (selected >= 0) drawSquare(canvas, selected, selPaint);
        // 合法落点
        for (int m : targets) {
            int t = m & 0xff;
            canvas.drawCircle(px(Board.colOf(t)), py(Board.rowOf(t)), cell * 0.12f, dotPaint);
        }

        // 棋子
        for (int s = 0; s < 90; s++) {
            int p = board.sq[s];
            if (p == Board.EMPTY) continue;
            drawPiece(canvas, s, p);
        }

        // 光标
        drawSquareStroke(canvas, cursor, cursorPaint);
    }

    private void drawSquare(Canvas canvas, int s, Paint paint) {
        float x = px(Board.colOf(s)), y = py(Board.rowOf(s));
        float h = cell / 2f;
        canvas.drawRect(x - h, y - h, x + h, y + h, paint);
    }

    private void drawSquareStroke(Canvas canvas, int s, Paint paint) {
        float x = px(Board.colOf(s)), y = py(Board.rowOf(s));
        float h = cell / 2f - 2f;
        canvas.drawRoundRect(new RectF(x - h, y - h, x + h, y + h), 8f, 8f, paint);
    }

    private void drawPiece(Canvas canvas, int s, int p) {
        float x = px(Board.colOf(s)), y = py(Board.rowOf(s));
        float radius = cell * 0.44f;
        canvas.drawCircle(x, y, radius, pieceFillPaint);
        canvas.drawCircle(x, y, radius, pieceStrokePaint);
        Paint tp = Board.color(p) > 0 ? redTextPaint : blackTextPaint;
        Paint.FontMetrics fm = tp.getFontMetrics();
        float baseline = y - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(pieceChar(p), x, baseline, tp);
    }

    private String pieceChar(int p) {
        int t = Board.type(p);
        boolean red = Board.color(p) > 0;
        switch (t) {
            case Board.KING: return red ? "帅" : "将";
            case Board.ADVISOR: return red ? "仕" : "士";
            case Board.ELEPHANT: return red ? "相" : "象";
            case Board.HORSE: return red ? "马" : "马";
            case Board.ROOK: return red ? "车" : "车";
            case Board.CANNON: return red ? "炮" : "炮";
            case Board.PAWN: return red ? "兵" : "卒";
            default: return "";
        }
    }

    // ---------- 输入 ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int r = Board.rowOf(cursor), c = Board.colOf(cursor);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (r > 0) cursor = Board.idx(r - 1, c);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (r < 9) {
                    cursor = Board.idx(r + 1, c);
                } else if (focusDownTarget != null) {
                    // 光标已在棋盘最底行：把焦点交给底部功能按钮栏
                    focusDownTarget.requestFocus();
                }
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (c > 0) cursor = Board.idx(r, c - 1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (c < 8) cursor = Board.idx(r, c + 1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                onCursorConfirm();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        int c = Math.round((event.getX() - ox) / cell);
        int r = Math.round((event.getY() - oy) / cell);
        if (!Board.inBoard(r, c)) return true;
        cursor = Board.idx(r, c);
        onCursorConfirm();
        return true;
    }

    private void onCursorConfirm() {
        if (!inputEnabled || board == null) return;
        int p = board.sq[cursor];
        if (selected < 0) {
            if (Board.color(p) == humanSide) {
                select(cursor);
            }
        } else {
            if (cursor == selected) {
                clearSelection();
            } else if (containsTarget(cursor)) {
                int move = (selected << 8) | cursor;
                clearSelection();
                if (listener != null) listener.onHumanMove(move);
            } else if (Board.color(p) == humanSide) {
                select(cursor);
            } else {
                clearSelection();
            }
        }
        invalidate();
    }

    private void select(int s) {
        selected = s;
        targets = board.legalMovesFrom(s);
    }

    private void clearSelection() {
        selected = -1;
        targets.clear();
    }

    private boolean containsTarget(int s) {
        for (int m : targets) {
            if ((m & 0xff) == s) return true;
        }
        return false;
    }
}
