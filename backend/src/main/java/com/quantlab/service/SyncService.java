package com.quantlab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quantlab.datasource.MarketDataProvider;
import com.quantlab.datasource.SymbolInfo;
import com.quantlab.domain.entity.DailyBar;
import com.quantlab.domain.entity.Stock;
import com.quantlab.mapper.DailyBarMapper;
import com.quantlab.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 行情数据同步。
 * <p>
 * 设计要点：
 * 1. Redis 分布式锁保证同一时刻只有一个同步任务；
 * 2. 按标的全量/增量拉取，已入库的最新日期作为增量起点；
 * 3. 进度写 Redis，前端可轮询，不阻塞主线程；
 * 4. 单标的失败不影响整体，失败计数留痕。
 */
@Slf4j
@Service
public class SyncService {

    private static final String LOCK_KEY = "quant:sync:lock";
    private static final String PROGRESS_KEY = "quant:sync:progress";
    private static final int BATCH_SIZE = 500;
    private static final int BARS_PER_CALL = 10000;

    private final MarketDataProvider provider;
    private final StockMapper stockMapper;
    private final DailyBarMapper barMapper;
    private final StringRedisTemplate redis;
    private final ThreadPoolTaskExecutor executor;

    @Value("${quant.sync.include-types:STOCK,ETF,INDEX}")
    private String includeTypes;

    @Value("${quant.sync.history-start:2018-01-01}")
    private String historyStart;

    @Value("${quant.sync.fetch-concurrency:4}")
    private int fetchConcurrency;

    public SyncService(MarketDataProvider provider,
                       StockMapper stockMapper,
                       DailyBarMapper barMapper,
                       StringRedisTemplate redis,
                       @Qualifier("syncExecutor") ThreadPoolTaskExecutor executor) {
        this.provider = provider;
        this.stockMapper = stockMapper;
        this.barMapper = barMapper;
        this.redis = redis;
        this.executor = executor;
    }

    // ------------------------------------------------------------
    // 标的清单
    // ------------------------------------------------------------

    public int syncSymbolList() {
        int count = 0;
        for (String type : includeTypes.split(",")) {
            String t = type.trim().toUpperCase();
            if (t.isEmpty()) {
                continue;
            }
            List<SymbolInfo> list;
            try {
                list = provider.fetchSymbolList(t);
            } catch (Exception e) {
                log.error("拉取 {} 清单失败", t, e);
                continue;
            }
            for (SymbolInfo info : list) {
                count += upsertStock(info, t);
            }
        }
        log.info("标的清单同步完成，影响 {} 条", count);
        return count;
    }

    private int upsertStock(SymbolInfo info, String type) {
        Stock exist = stockMapper.selectOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getCode, info.getCode()).last("LIMIT 1"));
        if (exist == null) {
            Stock s = new Stock();
            s.setCode(info.getCode());
            s.setMarket(info.getMarket() == null ? 0 : info.getMarket());
            s.setName(info.getName());
            s.setType(type);
            s.setLatestPrice(info.getPrice());
            s.setPctChg(info.getPctChg());
            s.setTurnover(info.getTurnover());
            s.setStatus(1);
            s.setBarCount(0);
            return stockMapper.insert(s);
        }
        exist.setName(info.getName());
        exist.setType(type);
        exist.setLatestPrice(info.getPrice());
        exist.setPctChg(info.getPctChg());
        exist.setTurnover(info.getTurnover());
        return stockMapper.updateById(exist);
    }

    // ------------------------------------------------------------
    // 日线同步
    // ------------------------------------------------------------

    /**
     * 异步触发热门标的同步。
     *
     * @return 是否成功拿到执行权
     */
    public boolean triggerDailySyncAsync(boolean incremental, int topLimit) {
        Boolean got = redis.opsForValue()
                .setIfAbsent(LOCK_KEY, String.valueOf(System.currentTimeMillis()), Duration.ofHours(3));
        if (!Boolean.TRUE.equals(got)) {
            return false;
        }
        executor.execute(() -> {
            try {
                if (topLimit > 0) {
                    runTopSync(topLimit, incremental);
                } else {
                    runDailySync(incremental);
                }
            } catch (Exception e) {
                log.error("日线同步异常", e);
            } finally {
                redis.delete(LOCK_KEY);
                redis.delete(PROGRESS_KEY);
            }
        });
        return true;
    }

    /**
     * 同步指定标的的日线，同步执行。
     */
    public Map<String, Object> runDailySync(boolean incremental) {
        List<Stock> stocks = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getStatus, 1));
        if (stocks.isEmpty()) {
            log.warn("标的表为空，先执行清单同步");
            syncSymbolList();
            stocks = stockMapper.selectList(new LambdaQueryWrapper<Stock>().eq(Stock::getStatus, 1));
        }
        return doSync(stocks, incremental);
    }

    /**
     * 只同步成交额靠前的一批标的。
     * <p>
     * 免费行情源对批量抓取有 IP 级限流，全市场同步必然被封，
     * 因此日常同步以热门标的为主，长尾标的走按需回源。
     */
    public Map<String, Object> runTopSync(int limit, boolean incremental) {
        int n = Math.min(2000, Math.max(20, limit));
        List<Stock> stocks = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getStatus, 1)
                .isNotNull(Stock::getTurnover)
                .orderByDesc(Stock::getTurnover)
                .last("LIMIT " + n));
        return doSync(stocks, incremental);
    }

    private Map<String, Object> doSync(List<Stock> stocks, boolean incremental) {
        long begin = System.currentTimeMillis();
        LocalDate defaultStart = LocalDate.parse(historyStart);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        int total = stocks.size();

        updateProgress("RUNNING", total, 0, 0, "初始化");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Stock stock : stocks) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    int rows = syncOne(stock, incremental, defaultStart);
                    if (rows >= 0) {
                        success.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("同步失败 {} {}: {}", stock.getCode(), stock.getName(), e.getMessage());
                }
                int done = success.get() + failed.get();
                if (done % 20 == 0) {
                    updateProgress("RUNNING", total, success.get(), failed.get(),
                            stock.getCode() + " " + stock.getName());
                }
            }, executor);
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long cost = System.currentTimeMillis() - begin;
        String status = failed.get() == 0 ? "SUCCESS" : "PARTIAL";
        updateProgress(status, total, success.get(), failed.get(), "耗时 " + (cost / 1000) + "s");
        log.info("日线同步结束 总数={} 成功={} 失败={} 耗时={}ms", total, success.get(), failed.get(), cost);

        Map<String, Object> r = new HashMap<>();
        r.put("total", total);
        r.put("success", success.get());
        r.put("failed", failed.get());
        r.put("costMs", cost);
        return r;
    }

    /**
     * 同步单只标的。
     *
     * @return 写入行数；-1 表示上游无数据
     */
    private int syncOne(Stock stock, boolean incremental, LocalDate defaultStart) {
        LocalDate from = defaultStart;
        if (incremental) {
            LocalDate max = barMapper.selectMaxDate(stock.getCode());
            if (max != null) {
                from = max.plusDays(1);
                if (from.isAfter(LocalDate.now())) {
                    return 0;
                }
            }
        }

        int market = stock.getMarket() == null ? 0 : stock.getMarket();
        List<DailyBar> bars = provider.fetchDailyBars(stock.getCode(), market, from, LocalDate.now());
        if (bars == null || bars.isEmpty()) {
            return -1;
        }

        List<DailyBar> todo = new ArrayList<>();
        for (DailyBar b : bars) {
            if (!b.getTradeDate().isBefore(from)) {
                todo.add(b);
            }
        }
        if (todo.isEmpty()) {
            return 0;
        }

        int written = 0;
        for (int i = 0; i < todo.size(); i += BATCH_SIZE) {
            List<DailyBar> slice = todo.subList(i, Math.min(i + BATCH_SIZE, todo.size()));
            written += barMapper.batchUpsert(slice);
        }

        LocalDate maxDate = todo.get(todo.size() - 1).getTradeDate();
        int count = barMapper.countByCode(stock.getCode());
        stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .eq(Stock::getCode, stock.getCode())
                .set(Stock::getLastBarDate, maxDate)
                .set(Stock::getBarCount, count));
        return written;
    }

    /**
     * 按增量起点估算需要拉取的K线根数，避免每次都拉全量。
     * 交易日约占自然日的 5/7，再留 30 根缓冲。
     */
    private int resolveLimit(LocalDate from) {
        if (from == null) {
            return BARS_PER_CALL;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, LocalDate.now());
        if (days <= 0) {
            return 30;
        }
        long estimate = days * 5 / 7 + 30;
        return (int) Math.min(BARS_PER_CALL, Math.max(30, estimate));
    }


    private void updateProgress(String status, int total, int success, int failed, String message) {
        try {
            Map<String, String> m = new HashMap<>();
            m.put("status", status);
            m.put("total", String.valueOf(total));
            m.put("success", String.valueOf(success));
            m.put("failed", String.valueOf(failed));
            m.put("message", message == null ? "" : message);
            m.put("updatedAt", String.valueOf(System.currentTimeMillis()));
            redis.opsForHash().putAll(PROGRESS_KEY, m);
            redis.expire(PROGRESS_KEY, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.debug("进度写入失败: {}", e.getMessage());
        }
    }

    public Map<Object, Object> getProgress() {
        return redis.opsForHash().entries(PROGRESS_KEY);
    }
}
