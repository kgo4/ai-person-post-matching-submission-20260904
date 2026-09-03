package com.example.matching.service.common;

import java.util.Map;

/**
 * 向量同步任务服务接口（M-09）。
 * <p>
 * 监听器只负责入队，后台任务负责写入 Milvus。
 * 业务唯一键（EMPLOYEE:{id} / POST:{id}）保证同一实体仅一条待办；执行幂等，
 * 以最新业务数据覆盖旧向量；失败指数退避（10s * 2^attempt，上限 5 分钟）、
 * 最大 10 次后置 FAILED，提供人工重放与失败指标。
 */
public interface VectorSyncTaskService {

    String ENTITY_EMPLOYEE = "EMPLOYEE";
    String ENTITY_POST = "POST";

    /**
     * 业务唯一键
     */
    static String businessKey(String entityType, Long entityId) {
        return entityType + ":" + entityId;
    }

    /**
     * 监听器调用：入队（唯一键存在则刷新为 PENDING 并重置重试），不执行向量写入。
     */
    void enqueue(String entityType, Long entityId, Map<String, Object> extra);

    /**
     * 定时扫描并处理待同步任务（调度器入口）。
     */
    void processPendingTasks();

    /**
     * 人工重放：将 FAILED 记录重置为 PENDING。
     */
    boolean replay(Long taskId);
}
