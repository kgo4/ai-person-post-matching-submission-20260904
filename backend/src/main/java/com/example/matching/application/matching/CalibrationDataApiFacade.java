package com.example.matching.application.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.matching.CalibrationRecordVO;
import com.example.matching.service.matching.CalibrationDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;

/**
 * 匹配校准数据门面。
 */
@Service
@RequiredArgsConstructor
public class CalibrationDataApiFacade {

    private final CalibrationDataService calibrationDataService;

    public PageResponse<CalibrationRecordVO> pageCalibration(long current, long size,
                                                              LocalDateTime startTime, LocalDateTime endTime,
                                                              Long postId, Boolean exportEnabled) {
        IPage<CalibrationRecordVO> page = calibrationDataService.pageCalibration(
                current, size, startTime, endTime, postId, exportEnabled);
        return PageResponse.from(page, item -> item);
    }

    public void exportCalibration(String format, LocalDateTime startTime, LocalDateTime endTime,
                                  Long postId, boolean includeDimensions, boolean maskPersonalData,
                                  OutputStream out) throws IOException {
        calibrationDataService.exportCalibration(format, startTime, endTime, postId,
                includeDimensions, maskPersonalData, out);
    }
}
