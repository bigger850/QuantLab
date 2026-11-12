package com.quantlab.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quantlab.common.ApiResponse;
import com.quantlab.domain.entity.Stock;
import com.quantlab.dto.KlineView;
import com.quantlab.service.KlineViewService;
import com.quantlab.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final KlineViewService klineViewService;

    public StockController(StockService stockService, KlineViewService klineViewService) {
        this.stockService = stockService;
        this.klineViewService = klineViewService;
    }

    @GetMapping("/page")
    public ApiResponse<Page<Stock>> page(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(stockService.page(type, keyword, page, Math.min(200, size)));
    }

    @GetMapping("/search")
    public ApiResponse<List<Stock>> search(@RequestParam String keyword,
                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(stockService.search(keyword, limit));
    }

    @GetMapping("/{code}")
    public ApiResponse<Stock> detail(@PathVariable String code) {
        return ApiResponse.ok(stockService.getByCode(code));
    }

    @GetMapping("/{code}/kline")
    public ApiResponse<KlineView> kline(@PathVariable String code,
                                        @RequestParam(defaultValue = "500") int limit,
                                        @RequestParam(defaultValue = "true") boolean indicators) {
        return ApiResponse.ok(klineViewService.build(code, limit, indicators));
    }
}
