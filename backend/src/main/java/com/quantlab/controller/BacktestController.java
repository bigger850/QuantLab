package com.quantlab.controller;

import com.quantlab.backtest.BacktestResult;
import com.quantlab.common.ApiResponse;
import com.quantlab.domain.entity.BacktestRecord;
import com.quantlab.dto.BacktestRequest;
import com.quantlab.service.BacktestService;
import com.quantlab.strategy.StrategyRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;
    private final StrategyRegistry registry;

    public BacktestController(BacktestService backtestService, StrategyRegistry registry) {
        this.backtestService = backtestService;
        this.registry = registry;
    }

    @GetMapping("/strategies")
    public ApiResponse<List<Map<String, String>>> strategies() {
        return ApiResponse.ok(registry.listBrief());
    }

    @PostMapping("/run")
    public ApiResponse<BacktestResult> run(@RequestBody BacktestRequest req) {
        return ApiResponse.ok(backtestService.run(req));
    }

    @GetMapping("/recent")
    public ApiResponse<List<BacktestRecord>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(backtestService.recent(limit));
    }
}
