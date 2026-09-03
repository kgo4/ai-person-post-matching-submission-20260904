package com.example.matching.service.matching.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 语义匹配全局最优分配求解器（M-05）
 * <p>
 * 解决逐项贪心选择最佳匹配造成的局部最优问题：
 * <ul>
 *   <li>小规模矩阵（max(rows, cols) &lt;= hungarianMaxDimension）：Hungarian 全局最优匹配（最大权重）</li>
 *   <li>大规模矩阵：贪心近似，控制 O(n²) 复杂度</li>
 * </ul>
 * <p>
 * 约定：
 * <ul>
 *   <li>scoreMatrix[i][j] = 第 i 个员工能力与第 j 个岗位要求的匹配分数</li>
 *   <li>分数 &lt;= 0 的格子视为不可匹配（虚拟 unmatched 节点），不会强制生成匹配结果</li>
 *   <li>输出 Assignment 携带原始行列索引，score &lt;= 0 或索引为 -1 表示未匹配</li>
 *   <li>不修改原始输入矩阵，结果确定（相同输入多次执行输出一致）</li>
 * </ul>
 */
public class SemanticAssignmentSolver {

    /** 默认 Hungarian 最大维度 */
    public static final int DEFAULT_HUNGARIAN_MAX_DIMENSION = 100;

    private final int hungarianMaxDimension;

    public SemanticAssignmentSolver() {
        this(DEFAULT_HUNGARIAN_MAX_DIMENSION);
    }

    public SemanticAssignmentSolver(int hungarianMaxDimension) {
        this.hungarianMaxDimension = Math.max(1, hungarianMaxDimension);
    }

    /**
     * 单条匹配结果（原始矩阵索引）
     *
     * @param rowIndex 员工能力行索引（原始矩阵），未匹配时为 -1
     * @param colIndex 岗位要求列索引（原始矩阵），未匹配时为 -1
     * @param score    匹配分数
     */
    public record Assignment(int rowIndex, int colIndex, double score) {
    }

    /**
     * 求解最大权重匹配
     *
     * @param scoreMatrix 评分矩阵（非空）
     * @return 匹配列表；分数 &lt;= 0 的格子不产生匹配
     */
    public List<Assignment> solve(double[][] scoreMatrix) {
        if (scoreMatrix == null || scoreMatrix.length == 0) {
            return List.of();
        }
        int rows = scoreMatrix.length;
        int cols = scoreMatrix[0].length;
        if (cols == 0) {
            return List.of();
        }
        // 小规模使用 Hungarian 以获得全局最优；大规模使用贪心以控制 O(n²) 复杂度。
        if (Math.max(rows, cols) > hungarianMaxDimension) {
            return solveGreedy(scoreMatrix, rows, cols);
        }
        return solveHungarian(scoreMatrix, rows, cols);
    }

    /**
     * 贪心近似：每轮选择全局最高分（含确定性 tie-break）的未占用配对。
     * <p>
     * tie-break 规则：先按 score 降序，再按行（能力）索引升序，再按列（要求）索引升序，
     * 保证相同输入多次执行结果一致。
     */
    private List<Assignment> solveGreedy(double[][] scores, int rows, int cols) {
        List<Assignment> result = new ArrayList<>();
        boolean[] rowUsed = new boolean[rows];
        boolean[] colUsed = new boolean[cols];

        while (true) {
            double bestScore = 0;
            int bestRow = -1;
            int bestCol = -1;
            for (int i = 0; i < rows; i++) {
                if (rowUsed[i]) {
                    continue;
                }
                for (int j = 0; j < cols; j++) {
                    if (colUsed[j]) {
                        continue;
                    }
                    double score = scores[i][j];
                    if (score <= 0) {
                        continue;
                    }
                    if (score > bestScore
                            || (score == bestScore && (bestRow == -1 || i < bestRow || (i == bestRow && j < bestCol)))) {
                        bestScore = score;
                        bestRow = i;
                        bestCol = j;
                    }
                }
            }
            if (bestRow < 0 || bestCol < 0) {
                break;
            }
            result.add(new Assignment(bestRow, bestCol, bestScore));
            rowUsed[bestRow] = true;
            colUsed[bestCol] = true;
        }
        return result;
    }

    /**
     * Hungarian（Kuhn-Munkres）最小成本完美匹配，O(n³)，n = max(rows, cols)。
     * <p>
     * 最大权重匹配通过 cost = maxScore - score 转换为最小成本问题；
     * 不可匹配格子（score &lt;= 0）成本为无穷大，虚拟行列用于支持非方阵。
     */
    private List<Assignment> solveHungarian(double[][] scores, int rows, int cols) {
        int n = Math.max(rows, cols);

        double maxScore = 0;
        for (double[] row : scores) {
            for (double value : row) {
                if (value > maxScore) {
                    maxScore = value;
                }
            }
        }

        // 虚拟行占用真实列的成本：必须高于任何真实匹配成本（真实匹配 cost <= maxScore - 0.85 <= 0.15），
        // 保证真实能力与真实要求优先配对。
        final double DUMMY_PENALTY = 1.0;
        final double INF = 1e18;

        double[][] cost = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i < rows && j < cols) {
                    cost[i][j] = scores[i][j] > 0 ? maxScore - scores[i][j] : INF;
                } else if (i >= rows && j >= cols) {
                    cost[i][j] = 0;
                } else if (i < rows) {
                    // 真实行（要求）未匹配：成本 0
                    cost[i][j] = 0;
                } else {
                    // 虚拟行占用真实列（能力被浪费）：小惩罚
                    cost[i][j] = DUMMY_PENALTY;
                }
            }
        }

        // CP-algorithms 版 Hungarian：1-based 索引
        double[] u = new double[n + 1];
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            Arrays.fill(minv, Double.MAX_VALUE);
            boolean[] used = new boolean[n + 1];
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.MAX_VALUE;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        List<Assignment> result = new ArrayList<>();
        for (int j = 1; j <= n; j++) {
            int row = p[j] - 1;
            int col = j - 1;
            if (row < rows && col < cols && scores[row][col] > 0) {
                result.add(new Assignment(row, col, scores[row][col]));
            }
        }
        return result;
    }
}
