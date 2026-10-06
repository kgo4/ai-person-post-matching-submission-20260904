package com.example.matching.mapper.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.common.JobLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobLockMapper extends BaseMapper<JobLock> {

    /**
     * CAS 抢占锁：仅当锁未被持有或已过期时更新。
     * 返回受影响行数（1=成功，0=失败）。
     */
    @Update("UPDATE job_lock SET locked_by = #{lockedBy}, locked_at = NOW(), expires_at = #{expiresAt} " +
            "WHERE lock_name = #{lockName} " +
            "AND (locked_by IS NULL OR expires_at < NOW())")
    int acquireLock(@Param("lockName") String lockName,
                    @Param("lockedBy") String lockedBy,
                    @Param("expiresAt") String expiresAt);

    /**
     * 释放锁
     */
    @Update("UPDATE job_lock SET locked_by = NULL, locked_at = NULL, expires_at = NULL " +
            "WHERE lock_name = #{lockName} AND locked_by = #{lockedBy}")
    int releaseLock(@Param("lockName") String lockName, @Param("lockedBy") String lockedBy);
}
