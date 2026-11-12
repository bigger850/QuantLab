package com.quantlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.backtest.BacktestEngine;
import com.quantlab.backtest.BacktestResult;
import com.quantlab.common.BizException;
import com.quantlab.domain.entity.BacktestRecord;
import com.quantlab.domain.entity.DailyBar;
import com.quantlab.domain.entity.Stock;
import com.quantlab.dto.BacktestRequest;
import com.quantlab.mapper.BacktestRecordMapper;
import com.quantlab.strategy.Strategy;
import com.quantlab.strategy.StrategyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BacktestService {

    private final StockService stockService;
    private final QuoteService quoteService;
    private final StrategyRegistry registry;
    private final BacktestEngine engine;
    private final BacktestRecordMapper recordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quant.backtest.max-bars:20000}")
    private int maxBars;

    public BacktestService(StockService stockService,
                           QuoteService quoteService,
                           StrategyRegistry registry,
                           BacktestEngine engine,
                           BacktestRecordMapper recordMapper) {
        this.stockService = stockService;
        this.quoteService = quoteService;
        this.registry = registry;
        this.engine = engine;
        this.recordMapper = recordMapper;
    }

    public BacktestResult run(BacktestRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException("请指定回测标的");
        }
        Stock stock = stockService.getByCode(req.getCode());
        Strategy strategy = registry.get(req.getStrategy());

        // 多取 250 根用于指标预热，避免开头一段因均线未成形而没有信号
        List<DailyBar> all = quoteService.getKline(req.getCode(), maxBars, false);
        if (all.size() < 30) {
            throw new BizException("历史数据不足（当前 " + all.size()
                    + " 根），请先同步该标的行情");
        }

        LocalDate from = req.getStartDate();
        LocalDate to = req.getEndDate();
        List<DailyBar> bars = new ArrayList<>();
        for (DailyBar b : all) {
            if (from != null && b.getTradeDate().isBefore(from)) {
                continue;
            }
            if (to != null && b.getTradeDate().isAfter(to)) {
                continue;
            }
            bars.add(b);
        }
        if (bars.size() < 20) {
            throw new BizException("指定区间内交易日不足（" + bars.size() + " 根）");
        }

        double[] signals = strategy.signals(bars, req.getParams());
        BigDecimal cash = req.getInitCash() == null ? BigDecimal.valueOf(100000) : req.getInitCash();
        if (cash.compareTo(BigDecimal.ONE) <= 0) {
            throw new BizException("初始资金必须大于 0");
        }

        BacktestResult result = engine.run(stock.getCode(), stock.getName(),
                strategy.code(), strategy.displayName(), bars, signals, cash);

        if (req.isSave()) {
            saveRecord(req, result);
        }
        return result;
    }

    private void saveRecord(BacktestRequest req, BacktestResult r) {
        try {
            BacktestRecord rec = new BacktestRecord();
            rec.setCode(r.getCode());
            rec.setName(r.getName());
            rec.setStrategy(r.getStrategy());
            rec.setStrategyName(r.getStrategyName());
            rec.setStartDate(r.getStartDate());
            rec.setEndDate(r.getEndDate());
            rec.setInitCash(r.getInitCash());
            rec.setFinalEquity(r.getFinalEquity());
            rec.setTotalReturn(r.getTotalReturn());
            rec.setAnnualReturn(r.getAnnualReturn());
            rec.setMaxDrawdown(r.getMaxDrawdown());
            rec.setSharpe(r.getSharpe());
            rec.setWinRate(r.getWinRate());
            rec.setProfitFactor(r.getProfitFactor());
            rec.setTradeCount(r.getTradeCount());
            rec.setBenchmarkReturn(r.getBenchmarkReturn());
            if (req.getParams() != null) {
                rec.setParamsJson(objectMapper.writeValueAsString(req.getParams()));
            }
            recordMapper.insert(rec);
        } catch (Exception e) {
            log.warn("回测记录保存失败: {}", e.getMessage());
        }
    }

    public List<BacktestRecord> recent(int limit) {
        return recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BacktestRecord>()
                        .orderByDesc(BacktestRecord::getId)
                        .last("LIMIT " + Math.min(100, Math.max(1, limit))));
    }
}
