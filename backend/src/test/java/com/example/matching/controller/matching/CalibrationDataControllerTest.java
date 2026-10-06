package com.example.matching.controller.matching;

import com.example.matching.application.matching.CalibrationDataApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.CalibrationRecordVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalibrationDataControllerTest {

    @Test
    void pageCalibrationReturnsFacadePage() {
        CalibrationDataApiFacade facade = mock(CalibrationDataApiFacade.class);
        CalibrationDataController controller = new CalibrationDataController(facade);

        CalibrationRecordVO vo = new CalibrationRecordVO(
                1L, 1L, 100L, 200L, null, null, null, null,
                null, null, null, null, 1, LocalDateTime.of(2024, 1, 15, 10, 0));
        PageResponse<CalibrationRecordVO> page = new PageResponse<>(List.of(vo), 1, 1, 20, 1);
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 31, 0, 0);
        when(facade.pageCalibration(1, 20, start, end, 200L, true)).thenReturn(page);

        R<PageResponse<CalibrationRecordVO>> response =
                controller.pageCalibration(1, 20, start, end, 200L, true);

        assertThat(response.getData()).isEqualTo(page);
        assertThat(response.getData().records()).containsExactly(vo);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void pageCalibrationPassesThroughNullFilters() {
        CalibrationDataApiFacade facade = mock(CalibrationDataApiFacade.class);
        CalibrationDataController controller = new CalibrationDataController(facade);

        PageResponse<CalibrationRecordVO> page = new PageResponse<>(List.of(), 0, 1, 20, 0);
        when(facade.pageCalibration(2, 20, null, null, null, null)).thenReturn(page);

        R<PageResponse<CalibrationRecordVO>> response =
                controller.pageCalibration(2, 20, null, null, null, null);

        assertThat(response.getData()).isEqualTo(page);
    }

    @Test
    void exportCalibrationJsonlStreamsToResponse() throws Exception {
        CalibrationDataApiFacade facade = mock(CalibrationDataApiFacade.class);
        CalibrationDataController controller = new CalibrationDataController(facade);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(sos);

        controller.exportCalibration("jsonl", null, null, null, true, true, response);

        verify(response).setContentType("application/x-jsonlines; charset=UTF-8");
        verify(facade).exportCalibration("jsonl", null, null, null, true, true, sos);
    }

    @Test
    void exportCalibrationCsvUsesCsvContentType() throws Exception {
        CalibrationDataApiFacade facade = mock(CalibrationDataApiFacade.class);
        CalibrationDataController controller = new CalibrationDataController(facade);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(sos);

        controller.exportCalibration("CSV", null, null, null, false, false, response);

        verify(response).setContentType("text/csv; charset=UTF-8");
        verify(facade).exportCalibration("csv", null, null, null, false, false, sos);
    }
}
