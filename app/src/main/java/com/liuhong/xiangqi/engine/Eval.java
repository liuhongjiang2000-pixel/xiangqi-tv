package com.liuhong.xiangqi.engine;

/**
 * 静态评估：子力价值 + 程序化位置分（低头 difficulty 也够用的启发式）。
 * 返回值为"红方减黑方"的分值，正数红优。
 */
public class Eval {

    // 按棋子类型索引
    public static final int[] VALUE = {
            0,        // EMPTY
            100000,   // KING
            120,      // ADVISOR
            120,      // ELEPHANT
            270,      // HORSE
            600,      // ROOK
            300,      // CANNON
            60        // PAWN
    };

    public static int evaluate(Board b) {
        int score = 0;
        for (int s = 0; s < 90; s++) {
            int p = b.sq[s];
            if (p == Board.EMPTY) continue;
            int v = VALUE[Board.type(p)] + positional(p, s);
            score += Board.color(p) > 0 ? v : -v;
        }
        return score;
    }

    /** 位置分（以红方视角计算，黑方通过对称行取负）。 */
    private static int positional(int p, int s) {
        int t = Board.type(p);
        int color = Board.color(p);
        int r = Board.rowOf(s);
        int c = Board.colOf(s);
        int redR = color > 0 ? r : 9 - r; // 对称到红方视角（推进方向）
        int bonus = 0;
        switch (t) {
            case Board.PAWN: {
                int advanced = 9 - redR; // 从己方底线出发走了几步
                bonus += advanced * 6;
                if (Board.crossedRiver(s, color)) bonus += 15;
                if (c >= 3 && c <= 5) bonus += 8; // 中卒更凶
                break;
            }
            case Board.HORSE:
            case Board.CANNON: {
                int center = 4 - Math.abs(c - 4);
                bonus += center * 6;
                if (redR >= 2 && redR <= 7) bonus += 8; // 活跃位置
                break;
            }
            case Board.ROOK: {
                int center = 4 - Math.abs(c - 4);
                bonus += center * 3;
                break;
            }
            case Board.ELEPHANT:
            case Board.ADVISOR: {
                // 鼓励留守九宫附近，轻度奖励原位
                bonus += 4;
                break;
            }
            default:
                break;
        }
        return bonus;
    }
}
