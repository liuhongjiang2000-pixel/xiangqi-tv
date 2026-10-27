package com.liuhong.xiangqi.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Negamax + alpha-beta 剪枝 + 吃子静态搜索（quiescence）。
 * 迭代加深，限时截止；低难度在根节点引入随机性，棋力随难度提升。
 */
public class Search {

    private static final int MATE = 100000;
    private static final int MAX_QDEPTH = 8;

    private final long deadline;
    private boolean timeUp;
    private int nodes;
    private final Random random = new Random();

    /** @param timeMs 思考时间上限 */
    public Search(long timeMs) {
        this.deadline = System.currentTimeMillis() + timeMs;
    }

    /**
     * 搜索最佳着法。
     *
     * @param board     当前局面（side 为 AI 方）
     * @param maxDepth  最大搜索深度
     * @param randMargin 根节点随机幅度（分）；>0 时会在接近最优的着法中随机挑选，降低棋力
     * @return 着法编码，无着法返回 -1
     */
    public int findBest(Board board, int maxDepth, int randMargin) {
        timeUp = false;
        nodes = 0;
        List<Integer> root = board.allLegal();
        if (root.isEmpty()) return -1;
        if (root.size() == 1) return root.get(0);

        int best = root.get(0);
        int bestScore = Integer.MIN_VALUE + 1;
        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = Integer.MIN_VALUE + 1;
            int iterBest = -1;
            List<int[]> scores = new ArrayList<int[]>(root.size());
            for (int m : root) {
                Board child = board.copyOf();
                child.make(m);
                int s = -negamax(child, depth - 1, Integer.MIN_VALUE + 1, -alpha, 1);
                if (timeUp) break;
                scores.add(new int[]{m, s});
                if (s > alpha) {
                    alpha = s;
                    iterBest = m;
                }
            }
            if (!timeUp && iterBest != -1) {
                best = iterBest;
                bestScore = alpha;
                // 下一层迭代把最佳着法排最前
                root.remove(Integer.valueOf(best));
                root.add(0, best);
            }
            if (timeUp) break;
            if (bestScore > MATE - 1000) break; // 已找到必杀
        }

        if (randMargin > 0) {
            List<Integer> candidates = new ArrayList<Integer>();
            for (int m : root) {
                Board child = board.copyOf();
                child.make(m);
                int s = -negamax(child, Math.min(2, maxDepth) - 1,
                        Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, 1);
                if (s >= bestScore - randMargin) candidates.add(m);
            }
            if (!candidates.isEmpty()) {
                best = candidates.get(random.nextInt(candidates.size()));
            }
        }
        return best;
    }

    private int negamax(Board b, int depth, int alpha, int beta, int ply) {
        nodes++;
        if ((nodes & 2047) == 0 && System.currentTimeMillis() > deadline) {
            timeUp = true;
            return 0;
        }
        if (depth <= 0) return quiesce(b, alpha, beta, 0);

        List<Integer> moves = b.allLegal();
        if (moves.isEmpty()) {
            return -(MATE - ply); // 将死或困毙：走子方负
        }
        orderMoves(b, moves);
        for (int m : moves) {
            Board child = b.copyOf();
            child.make(m);
            int s = -negamax(child, depth - 1, -beta, -alpha, ply + 1);
            if (timeUp) return 0;
            if (s >= beta) return beta;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    private int quiesce(Board b, int alpha, int beta, int qdepth) {
        nodes++;
        if ((nodes & 2047) == 0 && System.currentTimeMillis() > deadline) {
            timeUp = true;
            return 0;
        }
        int stand = sideScore(b);
        if (stand >= beta) return beta;
        if (stand > alpha) alpha = stand;
        if (qdepth >= MAX_QDEPTH) return alpha;

        // 只延伸吃子着法
        List<Integer> captures = new ArrayList<Integer>(40);
        List<Integer> pseudo = new ArrayList<Integer>(120);
        b.genAll(b.side, pseudo);
        for (int m : pseudo) {
            int to = m & 0xff;
            if (b.sq[to] != Board.EMPTY) captures.add(m);
        }
        orderMoves(b, captures);
        for (int m : captures) {
            if (!b.legal(m)) continue;
            Board child = b.copyOf();
            child.make(m);
            int s = -quiesce(child, -beta, -alpha, qdepth + 1);
            if (timeUp) return 0;
            if (s >= beta) return beta;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    /** 当前走子方视角的分数。 */
    private int sideScore(Board b) {
        int s = Eval.evaluate(b); // 红减黑
        return b.side > 0 ? s : -s;
    }

    /** MVV-LVA：按吃子价值排序。 */
    private void orderMoves(Board b, List<Integer> moves) {
        int n = moves.size();
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) {
            int m = moves.get(i);
            int victim = Board.type(b.sq[m & 0xff]);
            int attacker = Board.type(b.sq[m >>> 8]);
            keys[i] = victim != Board.EMPTY
                    ? Eval.VALUE[victim] * 16 - Eval.VALUE[attacker]
                    : 0;
        }
        // 简单插入排序（着法数少，避免装箱比较器开销）
        for (int i = 1; i < n; i++) {
            int km = keys[i], mm = moves.get(i);
            int j = i - 1;
            while (j >= 0 && keys[j] < km) {
                keys[j + 1] = keys[j];
                moves.set(j + 1, moves.get(j));
                j--;
            }
            keys[j + 1] = km;
            moves.set(j + 1, mm);
        }
    }
}
