package com.example.matching.utils;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * EasyExcel 读取监听器（泛型）
 * <p>
 * 分批读取 Excel，每批到达阈值时回调 consumer 处理，防止 OOM
 */
@Slf4j
public class ExcelListener<T> extends AnalysisEventListener<T> {

    private static final int BATCH_COUNT = 500;

    @Getter
    private final List<T> allData = new ArrayList<>();

    private final Consumer<List<T>> batchHandler;

    public ExcelListener(Consumer<List<T>> batchHandler) {
        this.batchHandler = batchHandler;
    }

    public ExcelListener() {
        this.batchHandler = null;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        allData.add(data);
        if (allData.size() >= BATCH_COUNT && batchHandler != null) {
            batchHandler.accept(new ArrayList<>(allData));
            allData.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (batchHandler != null && !allData.isEmpty()) {
            batchHandler.accept(new ArrayList<>(allData));
            allData.clear();
        }
        log.info("Excel读取完成，共{}行", context.readRowHolder().getRowIndex());
    }
}
