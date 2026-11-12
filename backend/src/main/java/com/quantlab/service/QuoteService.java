package com.quantlab.service;

import com.quantlab.datasource.MarketDataProvider;
import com.quantlab.datasource.SymbolInfo;
import com.quantlab.domain.entity.DailyBar;
import com.quantlab.mapper.DailyBarMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 行情查询。K线优先读本地库，库中无数据时回源并落库。
 */
@Slf4j
@Service
public class QuoteService {

    private static final String KLINE_KEY = "quant:kline:%s:%d";

    private final DailyBarMapper barMapper;
    private final MarketDataProvider provider;
    private final RedisTemplate<String, Object> redis;

    public QuoteService(DailyBarMapper barMapper,
                        MarketDataProvider provider,
                        RedisTemplate<String, Object> redis) {
        this.barMapper = barMapper;
        this.provider = provider;
        this.redis = redis;
    }

    /**
     * 取最近 limit 根K线，返回按时间升序。
     */
    @SuppressWarnings("unchecked")
    public List<DailyBar> getKline(String code, int limit, boolean useCache) {
        int n = Math.min(5000, Math.max(10, limit));
        String key = String.format(KLINE_KEY, code, n);
        if (useCache) {
            try {
                Object cached = redis.opsForValue().get(key);
                if (cached instanceof List<?> list && !list.isEmpty()) {
                    return (List<DailyBar>) list;
                }
            } catch (Exception e) {
                log.debug("K线缓存读取失败: {}", e.getMessage());
            }
        }

        List<DailyBar> bars = barMapper.selectLatest(code, n);
        if (bars.isEmpty()) {
            bars = fetchAndStore(code);
            if (bars.size() > n) {
                bars = new ArrayList<>(bars.subList(bars.size() - n, bars.size()));
            }
        } else {
            Collections.reverse(bars);
        }

        if (useCache && !bars.isEmpty()) {
            try {
                redis.opsForValue().set(key, bars, Duration.ofMinutes(5));
            } catch (Exception e) {
                log.debug("K线缓存写入失败: {}", e.getMessage());
            }
        }
        return bars;
    }

    /**
     * 本地无数据时回源拉取并落库。
     */
    public List<DailyBar> fetchAndStore(String code) {
        int market = com.quantlab.datasource.TencentClient.guessMarket(code);
        List<DailyBar> bars = provider.fetchDailyBars(code, market,
                java.time.LocalDate.of(2018, 1, 1), java.time.LocalDate.now());
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < bars.size(); i += 500) {
            barMapper.batchUpsert(bars.subList(i, Math.min(i + 500, bars.size())));
        }
        log.info("回源补齐 {} 共 {} 根K线", code, bars.size());
        return bars;
    }

    public Map<String, Object> realtime(List<String> codes) {
        List<SymbolInfo> quotes = provider.fetchQuotes(codes);
        return Map.of("items", quotes, "count", quotes.size());
    }
}
