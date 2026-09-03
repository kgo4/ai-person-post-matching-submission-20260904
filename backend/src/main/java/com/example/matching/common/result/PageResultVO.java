package com.example.matching.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页响应VO
 */
@Data
public class PageResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;

    public static <T> PageResultVO<T> of(IPage<T> page) {
        PageResultVO<T> vo = new PageResultVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setSize(page.getSize());
        vo.setCurrent(page.getCurrent());
        vo.setPages(page.getPages());
        return vo;
    }
}
