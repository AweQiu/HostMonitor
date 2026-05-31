package com.ylh.backend.Common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public PageResult() {}

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
    }

    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T, R> PageResult<R> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page, List<R> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
