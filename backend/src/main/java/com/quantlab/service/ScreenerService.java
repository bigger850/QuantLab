package com.quantlab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantlab.domain.entity.DailyBar;
import com.quantlab.domain.entity.Stock;
import com.quantlab.dto.ScreenerItem;
import com.quantlab.dto.ScreenerRequest;
import com.quantlab.mapper.DailyBarMapper;
import com.quantlab.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 因子筛选。
 * <p>
 * 实现方式：先按成交额取候选池，再逐只读取近期K线现算指标。
 * 候选池规模受控，避免全市场窗口函数扫描拖垮数据库。
 */
@Slf4j
@Service
public class ScreenerService {

    private static final int LOOKBACK = 120;

    private final StockMapper stockMapper;
    private final DailyBarMapper barMapper;

    public ScreenerService(StockMapper stockMapper, DailyBarMapper barMapper) {
        this.stockMapper = stockMapper;
        this.barMapper = barMapper;
    }

    public List<ScreenerItem> screen(ScreenerRequest req) {
        int pool = Math.min(1000, Math.max(20, req.getPoolSize()));
        List<Stock> candidates = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getType, req.getType() == null ? "STOCK" : req.getType().toUpperCase())
                .eq(Stock::getStatus, 1)
                .isNotNull(Stock::getTurnover)
                .orderByDesc(Stock::getTurnover)
                .last("LIMIT " + pool));

        List<ScreenerItem> hits = new ArrayList<>();
        for (Stock s : candidates) {
            try {
                List<DailyBar> bars = barMapper.selectLatest(s.getCode(), LOOKBACK);
                if (bars.size() < 65) {
                    continue;
                }
                // selectLatest 是倒序，翻正
                java.util.Collections.reverse(bars);
                ScreenerItem item = evaluate(s, bars);
                if (item != null && matches(item, req)) {
                    hits.add(item);
                }
            } catch (Exception e) {
                log.debug("筛选计算失败 {}: {}", s.getCode(), e.getMessage());
            }
        }

        hits.sort(Comparator.comparing(ScreenerItem::getReturn20,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int limit = Math.min(200, Math.max(1, req.getLimit()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    private ScreenerItem evaluate(Stock s, List<DailyBar> bars) {
        int n = bars.size();
        if (n < 65) {
            return null;
        }
        double[] close = new double[n];
        double[] amount = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = bars.get(i).getClose().doubleValue();
            amount[i] = bars.get(i).getAmount() == null ? 0 : bars.get(i).getAmount().doubleValue();
        }
        double last = close[n - 1];
        if (last <= 0) {
            return null;
        }

        ScreenerItem item = new ScreenerItem();
        item.setCode(s.getCode());
        item.setName(s.getName());
        item.setPrice(round2(last));
        item.setPctChg(s.getPctChg());
        item.setAmount(round2(amount[n - 1]));
        item.setLastDate(bars.get(n - 1).getTradeDate());

        item.setReturn5(round2(pctChange(close, n, 5)));
        item.setReturn20(round2(pctChange(close, n, 20)));
        item.setReturn60(round2(pctChange(close, n, 60)));
        item.setVolatility20(round2(annualizedVolatility(close, n, 20)));

        double peak = Double.MIN_VALUE;
        for (int i = n - 60; i < n; i++) {
            if (close[i] > peak) {
                peak = close[i];
            }
        }
        item.setDrawdown60(round2(peak > 0 ? (peak - last) / peak * 100 : 0));

        double sum = 0;
        for (int i = n - 20; i < n; i++) {
            sum += close[i];
        }
        double ma20 = sum / 20;
        item.setMa20(round2(ma20));
        item.setMa20Deviation(round2(ma20 > 0 ? (last - ma20) / ma20 * 100 : 0));
        return item;
    }

    private boolean matches(ScreenerItem item, ScreenerRequest req) {
        if (req.getMinAmount() != null
                && item.getAmount().compareTo(req.getMinAmount()) < 0) {
            return false;
        }
        if (req.getMinReturn20() != null
                && item.getReturn20().compareTo(req.getMinReturn20()) < 0) {
            return false;
        }
        if (req.getMaxReturn20() != null
                && item.getReturn20().compareTo(req.getMaxReturn20()) > 0) {
            return false;
        }
        if (req.getMaxVolatility() != null
                && item.getVolatility20().compareTo(req.getMaxVolatility()) > 0) {
            return false;
        }
        if (req.getMaxDrawdown60() != null
                && item.getDrawdown60().compareTo(req.getMaxDrawdown60()) > 0) {
            return false;
        }
        if (Boolean.TRUE.equals(req.getAboveMa20()) && item.getMa20Deviation().doubleValue() <= 0) {
            return false;
        }
        return true;
    }

    private static double pctChange(double[] close, int n, int days) {
        if (n <= days) {
            return 0;
        }
        double past = close[n - 1 - days];
        if (past <= 0) {
            return 0;
        }
        return (close[n - 1] - past) / past * 100;
    }

    private static double annualizedVolatility(double[] close, int n, int window) {
        int start = Math.max(1, n - window);
        double[] rets = new double[n - start];
        for (int i = start; i < n; i++) {
            rets[i - start] = close[i - 1] > 0 ? close[i] / close[i - 1] - 1 : 0;
        }
        if (rets.length < 2) {
            return 0;
        }
        double sum = 0;
        for (double r : rets) {
            sum += r;
        }
        double mean = sum / rets.length;
        double var = 0;
        for (double r : rets) {
            var += (r - mean) * (r - mean);
        }
        var /= (rets.length - 1);
        return Math.sqrt(var) * Math.sqrt(252) * 100;
    }

    private static BigDecimal round2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
