package com.liuhong.xiangqi.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋棋盘与着法生成。
 * 棋盘 10 行 x 9 列，索引 = row * 9 + col。
 * row 0 为黑方底线（顶部），row 9 为红方底线（底部）。
 * 棋子为带符号整数：正 = 红，负 = 黑，0 = 空。
 */
public class Board {

    public static final int EMPTY = 0;
    public static final int KING = 1;     // 帅/将
    public static final int ADVISOR = 2;  // 仕/士
    public static final int ELEPHANT = 3; // 相/象
    public static final int HORSE = 4;    // 马
    public static final int ROOK = 5;     // 车
    public static final int CANNON = 6;   // 炮
    public static final int PAWN = 7;     // 兵/卒

    public final int[] sq = new int[90];
    public int side; // +1 红方走，-1 黑方走

    public Board() {
        reset();
    }

    public static int idx(int r, int c) {
        return r * 9 + c;
    }

    public static int rowOf(int s) {
        return s / 9;
    }

    public static int colOf(int s) {
        return s % 9;
    }

    public static int color(int p) {
        return Integer.signum(p);
    }

    public static int type(int p) {
        return Math.abs(p);
    }

    public void reset() {
        for (int i = 0; i < 90; i++) sq[i] = EMPTY;
        // 黑方（上）
        sq[idx(0, 0)] = -ROOK;  sq[idx(0, 8)] = -ROOK;
        sq[idx(0, 1)] = -HORSE; sq[idx(0, 7)] = -HORSE;
        sq[idx(0, 2)] = -ELEPHANT; sq[idx(0, 6)] = -ELEPHANT;
        sq[idx(0, 3)] = -ADVISOR; sq[idx(0, 5)] = -ADVISOR;
        sq[idx(0, 4)] = -KING;
        sq[idx(2, 1)] = -CANNON; sq[idx(2, 7)] = -CANNON;
        sq[idx(3, 0)] = -PAWN; sq[idx(3, 2)] = -PAWN;
        sq[idx(3, 4)] = -PAWN; sq[idx(3, 6)] = -PAWN; sq[idx(3, 8)] = -PAWN;
        // 红方（下）
        sq[idx(9, 0)] = ROOK;  sq[idx(9, 8)] = ROOK;
        sq[idx(9, 1)] = HORSE; sq[idx(9, 7)] = HORSE;
        sq[idx(9, 2)] = ELEPHANT; sq[idx(9, 6)] = ELEPHANT;
        sq[idx(9, 3)] = ADVISOR; sq[idx(9, 5)] = ADVISOR;
        sq[idx(9, 4)] = KING;
        sq[idx(7, 1)] = CANNON; sq[idx(7, 7)] = CANNON;
        sq[idx(6, 0)] = PAWN; sq[idx(6, 2)] = PAWN;
        sq[idx(6, 4)] = PAWN; sq[idx(6, 6)] = PAWN; sq[idx(6, 8)] = PAWN;
        side = 1;
    }

    public static boolean inBoard(int r, int c) {
        return r >= 0 && r < 10 && c >= 0 && c < 9;
    }

    public static boolean inPalace(int s, int color) {
        int c = colOf(s);
        int r = rowOf(s);
        if (c < 3 || c > 5) return false;
        return color > 0 ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    public static boolean crossedRiver(int s, int color) {
        int r = rowOf(s);
        return color > 0 ? r <= 4 : r >= 5;
    }

    /** 生成 s 处棋子的全部伪合法着法（不考虑走后己方被将军）。着法编码：(from << 8) | to。 */
    public void genMovesAt(int s, List<Integer> out) {
        int p = sq[s];
        if (p == EMPTY) return;
        int color = color(p);
        int r = rowOf(s);
        int c = colOf(s);
        switch (type(p)) {
            case KING: {
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] dv : d) {
                    int r2 = r + dv[0], c2 = c + dv[1];
                    if (inBoard(r2, c2) && inPalace(idx(r2, c2), color)) {
                        addIf(out, s, idx(r2, c2), color);
                    }
                }
                // 白脸将：同线无子阻隔可直接吃对方将
                int step = color > 0 ? -1 : 1;
                int rr = r + step;
                while (inBoard(rr, c)) {
                    int q = sq[idx(rr, c)];
                    if (q != EMPTY) {
                        if (type(q) == KING && color(q) != color) {
                            out.add((s << 8) | idx(rr, c));
                        }
                        break;
                    }
                    rr += step;
                }
                break;
            }
            case ADVISOR: {
                int[][] d = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] dv : d) {
                    int r2 = r + dv[0], c2 = c + dv[1];
                    if (inBoard(r2, c2) && inPalace(idx(r2, c2), color)) {
                        addIf(out, s, idx(r2, c2), color);
                    }
                }
                break;
            }
            case ELEPHANT: {
                int[][] d = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
                for (int[] dv : d) {
                    int r2 = r + dv[0], c2 = c + dv[1];
                    if (!inBoard(r2, c2)) continue;
                    if (sq[idx((r + r2) / 2, (c + c2) / 2)] != EMPTY) continue; // 塞象眼
                    if (color > 0 && r2 < 5) continue;  // 红象不过河
                    if (color < 0 && r2 > 4) continue;  // 黑象不过河
                    addIf(out, s, idx(r2, c2), color);
                }
                break;
            }
            case HORSE: {
                int[][] d = {{-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {1, -2}, {-1, 2}, {1, 2}};
                int[][] leg = {{-1, 0}, {-1, 0}, {1, 0}, {1, 0}, {0, -1}, {0, -1}, {0, 1}, {0, 1}};
                for (int i = 0; i < 8; i++) {
                    int r2 = r + d[i][0], c2 = c + d[i][1];
                    if (!inBoard(r2, c2)) continue;
                    if (sq[idx(r + leg[i][0], c + leg[i][1])] != EMPTY) continue; // 蹩马腿
                    addIf(out, s, idx(r2, c2), color);
                }
                break;
            }
            case ROOK:
                slide(out, s, r, c, color, false);
                break;
            case CANNON:
                slide(out, s, r, c, color, true);
                break;
            case PAWN: {
                int fwd = color > 0 ? -1 : 1;
                int r2 = r + fwd;
                if (inBoard(r2, c)) addIf(out, s, idx(r2, c), color);
                if (crossedRiver(s, color)) {
                    if (c > 0) addIf(out, s, idx(r, c - 1), color);
                    if (c < 8) addIf(out, s, idx(r, c + 1), color);
                }
                break;
            }
            default:
                break;
        }
    }

    private void addIf(List<Integer> out, int from, int to, int color) {
        if (color(sq[to]) != color) out.add((from << 8) | to);
    }

    private void slide(List<Integer> out, int s, int r, int c, int color, boolean cannon) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dv : d) {
            int r2 = r + dv[0], c2 = c + dv[1];
            boolean jumped = false;
            while (inBoard(r2, c2)) {
                int t = idx(r2, c2);
                int q = sq[t];
                if (!jumped) {
                    if (q == EMPTY) {
                        if (!cannon) out.add((s << 8) | t);
                    } else {
                        if (cannon) {
                            jumped = true;
                        } else {
                            // 车：遇到敌子可吃（然后停止），己子阻挡
                            if (color(q) != color) out.add((s << 8) | t);
                            break;
                        }
                    }
                } else {
                    if (q != EMPTY) {
                        if (color(q) != color) out.add((s << 8) | t);
                        break;
                    }
                }
                r2 += dv[0];
                c2 += dv[1];
            }
        }
    }

    public void genAll(int color, List<Integer> out) {
        for (int s = 0; s < 90; s++) {
            if (color(sq[s]) == color) genMovesAt(s, out);
        }
    }

    public void make(int m) {
        int from = m >>> 8;
        int to = m & 0xff;
        sq[to] = sq[from];
        sq[from] = EMPTY;
        side = -side;
    }

    public int kingOf(int color) {
        for (int i = 0; i < 90; i++) {
            if (sq[i] == color * KING) return i;
        }
        return -1;
    }

    /** target 格是否被 byColor 一方攻击（含白脸将规则）。 */
    public boolean attacked(int target, int byColor) {
        List<Integer> ms = new ArrayList<Integer>(80);
        genAll(byColor, ms);
        for (int m : ms) {
            if ((m & 0xff) == target) return true;
        }
        return false;
    }

    /** 着法是否合法（走后己方将不被攻击）。 */
    public boolean legal(int m) {
        int from = m >>> 8;
        if (from < 0 || from >= 90) return false;
        int color = color(sq[from]);
        Board b = copyOf();
        b.make(m);
        int king = b.kingOf(color); // 走子后再找将位（将本身可能移动）
        if (king == -1) return true;
        return !b.attacked(king, -color);
    }

    public List<Integer> allLegal() {
        List<Integer> pseudo = new ArrayList<Integer>(120);
        genAll(side, pseudo);
        List<Integer> legal = new ArrayList<Integer>(pseudo.size());
        for (int m : pseudo) {
            if (legal(m)) legal.add(m);
        }
        return legal;
    }

    public List<Integer> legalMovesFrom(int s) {
        List<Integer> pseudo = new ArrayList<Integer>(40);
        genMovesAt(s, pseudo);
        List<Integer> legal = new ArrayList<Integer>(pseudo.size());
        for (int m : pseudo) {
            if (legal(m)) legal.add(m);
        }
        return legal;
    }

    public Board copyOf() {
        Board b = new Board();
        System.arraycopy(sq, 0, b.sq, 0, 90);
        b.side = side;
        return b;
    }

    /** 局面哈希（含走子方），用于长打/重复局面判定。 */
    public long hash() {
        long h = side;
        for (int i = 0; i < 90; i++) {
            h = h * 131 + sq[i] + 7;
        }
        return h;
    }

    /** 对局状态：0 进行中；1 红胜；2 黑胜（含将死与困毙）。 */
    public static final int ONGOING = 0;
    public static final int RED_WIN = 1;
    public static final int BLACK_WIN = 2;

    public int status() {
        if (!allLegal().isEmpty()) return ONGOING;
        return side > 0 ? BLACK_WIN : RED_WIN;
    }

    /** 当前走子方是否被将军。 */
    public boolean inCheck() {
        int king = kingOf(side);
        if (king == -1) return false;
        return attacked(king, -side);
    }
}
