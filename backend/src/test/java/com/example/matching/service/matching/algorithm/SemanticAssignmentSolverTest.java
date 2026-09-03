package com.example.matching.service.matching.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-05 测试：语义匹配全局最优分配
 * <p>
 * 小规模矩阵使用 Hungarian 全局最优匹配，大规模矩阵使用贪心近似；
 * 低分不强制匹配；相同输入多次执行结果一致。
 */
class SemanticAssignmentSolverTest {

    private final SemanticAssignmentSolver solver = new SemanticAssignmentSolver();

    // ===== 测试 1：贪心不是全局最优的矩阵 =====

    @Test
    @DisplayName("贪心次优矩阵：Hungarian 找到全局最优（A->Y, B->X）")
    void greedySuboptimalMatrix_findsGlobalOptimum() {
        // A -> X = 0.90, A -> Y = 0.80, B -> X = 0.89, B -> Y = 0.10
        double[][] scores = {
                {0.90, 0.80},
                {0.89, 0.10}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = solver.solve(scores);

        // 全局最优：A(0)->Y(1)=0.80 + B(1)->X(0)=0.89，总分 1.69 > 贪心的 1.00
        assertThat(assignments).hasSize(2);
        double total = assignments.stream().mapToDouble(SemanticAssignmentSolver.Assignment::score).sum();
        assertThat(total).isEqualTo(1.69, org.assertj.core.data.Offset.offset(1e-9));

        assertThat(assignments).anyMatch(a -> a.rowIndex() == 0 && a.colIndex() == 1 && a.score() == 0.80);
        assertThat(assignments).anyMatch(a -> a.rowIndex() == 1 && a.colIndex() == 0 && a.score() == 0.89);
    }

    // ===== 测试 2：非方阵矩阵 =====

    @Test
    @DisplayName("非方阵：能力多于要求（3x2）")
    void nonSquareMatrix_moreAbilitiesThanRequirements() {
        double[][] scores = {
                {0.95, 0.10},
                {0.10, 0.92},
                {0.50, 0.30}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = solver.solve(scores);

        // 一对一：每个要求最多被一个能力占用
        assertThat(assignments).hasSize(2);
        assertThat(assignments).allMatch(a -> a.rowIndex() >= 0 && a.colIndex() >= 0);
        assertThat(assignments.stream().map(SemanticAssignmentSolver.Assignment::rowIndex).distinct()).hasSize(2);
        assertThat(assignments.stream().map(SemanticAssignmentSolver.Assignment::colIndex).distinct()).hasSize(2);
        double total = assignments.stream().mapToDouble(SemanticAssignmentSolver.Assignment::score).sum();
        assertThat(total).isEqualTo(0.95 + 0.92, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("非方阵：要求多于能力（2x3）")
    void nonSquareMatrix_moreRequirementsThanAbilities() {
        double[][] scores = {
                {0.98, 0.10, 0.50},
                {0.10, 0.10, 0.97}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = solver.solve(scores);

        assertThat(assignments).hasSize(2);
        assertThat(assignments.stream().map(SemanticAssignmentSolver.Assignment::colIndex).distinct()).hasSize(2);
        double total = assignments.stream().mapToDouble(SemanticAssignmentSolver.Assignment::score).sum();
        assertThat(total).isEqualTo(0.98 + 0.97, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ===== 测试 3：低于阈值不匹配 =====

    @Test
    @DisplayName("低于阈值的低分不会强制生成匹配结果")
    void lowScores_doNotForceMatches() {
        double[][] scores = {
                {0.90, 0},
                {0, 0}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = solver.solve(scores);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).rowIndex()).isEqualTo(0);
        assertThat(assignments.get(0).colIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("全零矩阵不产生任何匹配")
    void allZeroMatrix_noMatches() {
        double[][] scores = {
                {0, 0},
                {0, 0}
        };

        assertThat(solver.solve(scores)).isEmpty();
    }

    // ===== 测试 4：大矩阵走贪心 =====

    @Test
    @DisplayName("维度超过阈值时使用贪心近似而非 Hungarian")
    void largeMatrix_usesGreedyFallback() {
        // 阈值 2：3x2 矩阵超过阈值 -> 贪心
        SemanticAssignmentSolver smallThresholdSolver = new SemanticAssignmentSolver(2);
        double[][] scores = {
                {0.90, 0.80},
                {0.89, 0.10},
                {0.10, 0.10}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = smallThresholdSolver.solve(scores);

        // 贪心先取全局最高 0.90（row0,col0），再取 0.10（row1,col1）—— 次优结果证明走的是贪心
        assertThat(assignments).hasSize(2);
        assertThat(assignments).anyMatch(a -> a.rowIndex() == 0 && a.colIndex() == 0 && a.score() == 0.90);
        double total = assignments.stream().mapToDouble(SemanticAssignmentSolver.Assignment::score).sum();
        assertThat(total).isEqualTo(0.90 + 0.10, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("维度等于阈值时仍使用 Hungarian")
    void dimensionEqualToThreshold_usesHungarian() {
        SemanticAssignmentSolver solverWithThreshold2 = new SemanticAssignmentSolver(2);
        double[][] scores = {
                {0.90, 0.80},
                {0.89, 0.10}
        };

        List<SemanticAssignmentSolver.Assignment> assignments = solverWithThreshold2.solve(scores);

        // Hungarian 全局最优：0.80 + 0.89 = 1.69
        double total = assignments.stream().mapToDouble(SemanticAssignmentSolver.Assignment::score).sum();
        assertThat(total).isEqualTo(1.69, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ===== 测试 5：结果确定性 =====

    @Test
    @DisplayName("相同输入多次执行输出一致")
    void deterministic_acrossRuns() {
        double[][] scores = {
                {0.95, 0.90, 0.85},
                {0.80, 0.91, 0.70},
                {0.60, 0.65, 0.88}
        };

        List<SemanticAssignmentSolver.Assignment> first = solver.solve(scores);
        for (int i = 0; i < 10; i++) {
            List<SemanticAssignmentSolver.Assignment> again = solver.solve(scores);
            assertThat(again).containsExactlyInAnyOrderElementsOf(first);
        }
        assertThat(first).hasSize(3);
    }

    @Test
    @DisplayName("solve 不修改原始输入矩阵")
    void solve_doesNotMutateInputMatrix() {
        double[][] scores = {
                {0.90, 0.80},
                {0.89, 0.10}
        };
        double[][] snapshot = {
                {0.90, 0.80},
                {0.89, 0.10}
        };

        solver.solve(scores);

        assertThat(scores).isDeepEqualTo(snapshot);
    }
}
