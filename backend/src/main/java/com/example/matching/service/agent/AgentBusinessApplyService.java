package com.example.matching.service.agent;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;

/**
 * Agent 业务应用服务接口
 * <p>
 * 负责将 Agent 输出的能力声明经过 Harness 校验后写入业务表。
 * 所有业务写入都必须经过 Harness 校验。
 *
 * @author system
 */
public interface AgentBusinessApplyService {

    /**
     * 应用人员能力提取结果到业务表
     * <p>
     * 流程：
     * 1. 遍历每个 PersonAbilityClaim
     * 2. 调用 Harness 校验
     * 3. PASS: 写入 emp_ability
     * 4. REVIEW: 写入人员能力候选表
     * 5. BLOCK: 只保留 Harness 日志
     *
     * @param extractionResult 提取结果
     * @return 应用结果
     */
    PersonAbilityApplyResult applyPersonAbilities(PersonAbilityExtractionResult extractionResult);

    /**
     * Applies claims with optional coalescing of employee-wide refresh work.
     * Batch importers should set {@code coalesceEmployeeRefresh} to true.
     */
    PersonAbilityApplyResult applyPersonAbilities(PersonAbilityExtractionResult extractionResult,
                                                   boolean coalesceEmployeeRefresh);

    /**
     * 应用岗位能力提取结果到业务表
     * <p>
     * 流程：
     * 1. 遍历每个 PostAbilityClaim
     * 2. 调用 Harness 校验
     * 3. PASS: 写入 post_ability_model
     * 4. REVIEW: 写入岗位能力候选表
     * 5. BLOCK: 只保留 Harness 日志
     *
     * @param extractionResult 提取结果
     * @return 应用结果
     */
    PostAbilityApplyResult applyPostAbilities(PostAbilityExtractionResult extractionResult);

    /**
     * 人员能力应用结果
     */
    class PersonAbilityApplyResult {
        /** 总声明数 */
        private int totalClaims;
        /** 通过数 */
        private int passCount;
        /** 审核数 */
        private int reviewCount;
        /** 阻止数 */
        private int blockCount;
        /** 错误数 */
        private int errorCount;

        public PersonAbilityApplyResult() {
        }

        public PersonAbilityApplyResult(int totalClaims, int passCount, int reviewCount, int blockCount, int errorCount) {
            this.totalClaims = totalClaims;
            this.passCount = passCount;
            this.reviewCount = reviewCount;
            this.blockCount = blockCount;
            this.errorCount = errorCount;
        }

        public int getTotalClaims() {
            return totalClaims;
        }

        public void setTotalClaims(int totalClaims) {
            this.totalClaims = totalClaims;
        }

        public int getPassCount() {
            return passCount;
        }

        public void setPassCount(int passCount) {
            this.passCount = passCount;
        }

        public int getReviewCount() {
            return reviewCount;
        }

        public void setReviewCount(int reviewCount) {
            this.reviewCount = reviewCount;
        }

        public int getBlockCount() {
            return blockCount;
        }

        public void setBlockCount(int blockCount) {
            this.blockCount = blockCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public void setErrorCount(int errorCount) {
            this.errorCount = errorCount;
        }
    }

    /**
     * 岗位能力应用结果
     */
    class PostAbilityApplyResult {
        /** 总声明数 */
        private int totalClaims;
        /** 通过数 */
        private int passCount;
        /** 审核数 */
        private int reviewCount;
        /** 阻止数 */
        private int blockCount;
        /** 错误数 */
        private int errorCount;

        public PostAbilityApplyResult() {
        }

        public PostAbilityApplyResult(int totalClaims, int passCount, int reviewCount, int blockCount, int errorCount) {
            this.totalClaims = totalClaims;
            this.passCount = passCount;
            this.reviewCount = reviewCount;
            this.blockCount = blockCount;
            this.errorCount = errorCount;
        }

        public int getTotalClaims() {
            return totalClaims;
        }

        public void setTotalClaims(int totalClaims) {
            this.totalClaims = totalClaims;
        }

        public int getPassCount() {
            return passCount;
        }

        public void setPassCount(int passCount) {
            this.passCount = passCount;
        }

        public int getReviewCount() {
            return reviewCount;
        }

        public void setReviewCount(int reviewCount) {
            this.reviewCount = reviewCount;
        }

        public int getBlockCount() {
            return blockCount;
        }

        public void setBlockCount(int blockCount) {
            this.blockCount = blockCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public void setErrorCount(int errorCount) {
            this.errorCount = errorCount;
        }
    }
}
