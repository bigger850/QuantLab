package com.quantlab.job;

import com.quantlab.service.SyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步任务。
 * <p>
 * 交易日 15:30 做增量同步（收盘后数据已定稿）；
 * 次日 09:15 刷新一次快照行情，覆盖盘前竞价的最新价。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "quant.sync.enabled", havingValue = "true", matchIfMissing = true)
public class DailySyncJob {

    private final SyncService syncService;

    public DailySyncJob(SyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void syncAfterClose() {
        log.info("[定时任务] 收盘后增量同步开始（成交额前 500）");
        boolean started = syncService.triggerDailySyncAsync(true, 500);
        if (!started) {
            log.warn("[定时任务] 已有同步任务在跑，本次跳过");
        }
    }

    @Scheduled(cron = "0 15 9 * * MON-FRI")
    public void refreshSymbolsBeforeOpen() {
        log.info("[定时任务] 盘前刷新标的快照");
        try {
            syncService.syncSymbolList();
        } catch (Exception e) {
            log.error("[定时任务] 盘前刷新失败", e);
        }
    }
}
