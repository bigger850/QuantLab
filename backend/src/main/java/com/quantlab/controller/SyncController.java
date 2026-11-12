package com.quantlab.controller;

import com.quantlab.common.ApiResponse;
import com.quantlab.service.StockService;
import com.quantlab.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final StockService stockService;

    public SyncController(SyncService syncService, StockService stockService) {
        this.syncService = syncService;
        this.stockService = stockService;
    }

    @PostMapping("/symbols")
    public ApiResponse<Map<String, Object>> syncSymbols() {
        int count = syncService.syncSymbolList();
        Map<String, Object> m = new HashMap<>();
        m.put("affected", count);
        m.put("stockCount", stockService.count());
        return ApiResponse.ok(m);
    }

    /**
     * 触发日线同步。
     *
     * @param incremental 是否增量
     * @param top         只同步成交额前 N 只；0 表示全市场
     */
    @PostMapping("/daily")
    public ApiResponse<Map<String, Object>> syncDaily(
            @RequestParam(defaultValue = "true") boolean incremental,
            @RequestParam(defaultValue = "300") int top) {
        boolean started = syncService.triggerDailySyncAsync(incremental, top);
        Map<String, Object> m = new HashMap<>();
        m.put("started", started);
        m.put("message", started
                ? "同步任务已提交，可通过 /api/sync/progress 查看进度"
                : "已有同步任务在执行中");
        return ApiResponse.ok(m);
    }

    @GetMapping("/progress")
    public ApiResponse<Map<Object, Object>> progress() {
        return ApiResponse.ok(syncService.getProgress());
    }
}
