package com.quantlab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantlab.common.ApiResponse;
import com.quantlab.domain.entity.Stock;
import com.quantlab.mapper.DailyBarMapper;
import com.quantlab.mapper.StockMapper;
import com.quantlab.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final StockMapper stockMapper;
    private final DailyBarMapper barMapper;
    private final SyncService syncService;

    public DashboardController(StockMapper stockMapper,
                               DailyBarMapper barMapper,
                               SyncService syncService) {
        this.stockMapper = stockMapper;
        this.barMapper = barMapper;
        this.syncService = syncService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> m = new HashMap<>();
        long total = stockMapper.selectCount(new LambdaQueryWrapper<Stock>().eq(Stock::getStatus, 1));
        m.put("symbolCount", total);
        m.put("withBars", stockMapper.countWithBars());
        m.put("latestTradeDate", barMapper.latestGlobalDate());

        List<Map<String, Object>> breadth = stockMapper.marketBreadth();
        if (!breadth.isEmpty() && breadth.get(0) != null) {
            m.putAll(breadth.get(0));
        }

        List<Stock> topTurnover = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getStatus, 1)
                .eq(Stock::getType, "STOCK")
                .isNotNull(Stock::getTurnover)
                .orderByDesc(Stock::getTurnover)
                .last("LIMIT 10"));
        m.put("topTurnover", topTurnover);

        List<Stock> topGainers = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getStatus, 1)
                .eq(Stock::getType, "STOCK")
                .isNotNull(Stock::getPctChg)
                .orderByDesc(Stock::getPctChg)
                .last("LIMIT 10"));
        m.put("topGainers", topGainers);

        List<Stock> topLosers = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getStatus, 1)
                .eq(Stock::getType, "STOCK")
                .isNotNull(Stock::getPctChg)
                .orderByAsc(Stock::getPctChg)
                .last("LIMIT 10"));
        m.put("topLosers", topLosers);

        m.put("syncProgress", syncService.getProgress());
        m.put("serverDate", LocalDate.now().toString());
        return ApiResponse.ok(m);
    }
}
