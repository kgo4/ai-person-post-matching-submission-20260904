package com.example.matching.converter.matching;

import com.example.matching.dto.matching.api.MatchingRecordResponse;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.vo.matching.MatchingResultVO;
import org.mapstruct.Mapper;

/**
 * 匹配记录对象转换器（MapStruct自动生成实现）
 */
@Mapper(componentModel = "spring")
public interface MatchingRecordConverter {

    /**
     * Entity -> 匹配结果VO
     */
    MatchingResultVO toMatchingResultVO(MatchingRecord entity);

    /**
     * M17：Entity -> 匹配结果 API 响应（同名字段自动映射）
     */
    MatchingRecordResponse toResponse(MatchingRecord entity);
}
