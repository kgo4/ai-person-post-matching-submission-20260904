package com.example.matching.controller.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.post.PostCleaningApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostCleaningRecordPageQuery;
import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.dto.post.PostCleaningResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostCleaningRecordControllerTest {

    @Test
    void pageRecordsReturnsCleaningRecordPage() {
        PostCleaningApiFacade facade = mock(PostCleaningApiFacade.class);
        PostCleaningRecordController controller = new PostCleaningRecordController(facade);

        PostCleaningRecordPageQuery query = new PostCleaningRecordPageQuery();
        Page<PostCleaningRecordVO> page = new Page<>();
        page.setRecords(java.util.List.of(new PostCleaningRecordVO()));
        when(facade.pageRecords(query)).thenReturn(page);

        R<Page<PostCleaningRecordVO>> response = controller.pageRecords(query);

        assertThat(response.getData()).isSameAs(page);
    }

    @Test
    void getRecordDetailReturnsRecordWhenFound() {
        PostCleaningApiFacade facade = mock(PostCleaningApiFacade.class);
        PostCleaningRecordController controller = new PostCleaningRecordController(facade);

        PostCleaningRecordVO record = new PostCleaningRecordVO();
        record.setId(1L);
        record.setRawPostName("Java开发工程师");
        when(facade.getRecordDetail(1L)).thenReturn(record);

        R<PostCleaningRecordVO> response = controller.getRecordDetail(1L);

        assertThat(response.getData()).isSameAs(record);
    }

    @Test
    void getRecordDetailFailsWhenRecordMissing() {
        PostCleaningApiFacade facade = mock(PostCleaningApiFacade.class);
        PostCleaningRecordController controller = new PostCleaningRecordController(facade);

        when(facade.getRecordDetail(999L)).thenReturn(null);

        R<PostCleaningRecordVO> response = controller.getRecordDetail(999L);

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("清洗记录不存在");
    }

    @Test
    void reparseReturnsCleaningResult() {
        PostCleaningApiFacade facade = mock(PostCleaningApiFacade.class);
        PostCleaningRecordController controller = new PostCleaningRecordController(facade);

        PostCleaningResult result = new PostCleaningResult();
        result.setCleanedPostName("Java开发工程师");
        when(facade.reparse(1L)).thenReturn(result);

        R<PostCleaningResult> response = controller.reparse(1L);

        assertThat(response.getData()).isSameAs(result);
    }
}
