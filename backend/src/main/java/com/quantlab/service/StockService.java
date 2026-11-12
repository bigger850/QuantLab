package com.quantlab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quantlab.common.BizException;
import com.quantlab.domain.entity.Stock;
import com.quantlab.mapper.StockMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class StockService {

    private final StockMapper stockMapper;

    public StockService(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    public Page<Stock> page(String type, String keyword, int page, int size) {
        LambdaQueryWrapper<Stock> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            qw.eq(Stock::getType, type.toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Stock::getCode, kw).or().like(Stock::getName, kw));
        }
        qw.eq(Stock::getStatus, 1);
        qw.orderByDesc(Stock::getTurnover);
        return stockMapper.selectPage(new Page<>(page, size), qw);
    }

    public Stock getByCode(String code) {
        Stock s = stockMapper.selectOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getCode, code).last("LIMIT 1"));
        if (s == null) {
            throw new BizException("标的不存在: " + code);
        }
        return s;
    }

    public List<Stock> search(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.trim();
        return stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .and(w -> w.like(Stock::getCode, kw).or().like(Stock::getName, kw))
                .eq(Stock::getStatus, 1)
                .orderByDesc(Stock::getTurnover)
                .last("LIMIT " + Math.min(50, Math.max(1, limit))));
    }

    public long count() {
        return stockMapper.selectCount(new LambdaQueryWrapper<Stock>().eq(Stock::getStatus, 1));
    }
}
