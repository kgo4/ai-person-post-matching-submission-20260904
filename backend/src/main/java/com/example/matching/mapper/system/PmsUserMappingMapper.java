package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.PmsUserMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PMS用户映射 Mapper
 */
@Mapper
public interface PmsUserMappingMapper extends BaseMapper<PmsUserMapping> {

    /**
     * 根据本地员工ID查找映射
     */
    @Select("SELECT * FROM pms_user_mapping WHERE emp_id = #{empId} LIMIT 1")
    PmsUserMapping selectByEmpId(@Param("empId") Long empId);

    /**
     * 根据PMS用户ID查找映射
     */
    @Select("SELECT * FROM pms_user_mapping WHERE pms_user_id = #{pmsUserId} LIMIT 1")
    PmsUserMapping selectByPmsUserId(@Param("pmsUserId") Long pmsUserId);
}
