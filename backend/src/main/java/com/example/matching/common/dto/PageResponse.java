package com.example.matching.common.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
    List<T> records,
    long total,
    long current,
    long size,
    long pages
) implements Serializable {

    public static <S, T> PageResponse<T> from(IPage<S> page, Function<S, T> converter) {
        return new PageResponse<>(
            page.getRecords().stream().map(converter).toList(),
            page.getTotal(), page.getCurrent(), page.getSize(), page.getPages()
        );
    }
}
