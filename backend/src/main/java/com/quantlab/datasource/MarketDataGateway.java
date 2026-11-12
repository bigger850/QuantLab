package com.quantlab.datasource;

import com.quantlab.domain.entity.DailyBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源网关：清单走新浪，K线与实时走腾讯。
 * <p>
 * 之所以拆两个源，是因为东财接口在本机网络下连接被重置，
 * 而新浪与腾讯均可用。分层设计让后续换源只需改这一处。
 */
@Slf4j
@Component
public class MarketDataGateway implements MarketDataProvider {

    /** 单次请求覆盖的自然日跨度，约 2 年，稳妥落在上游 640 根限制内 */
    private static final int SEGMENT_YEARS = 2;
    private static final int MAX_SEGMENTS = 20;

    private final SinaClient sina;
    private final TencentClient tencent;

    public MarketDataGateway(SinaClient sina, TencentClient tencent) {
        this.sina = sina;
        this.tencent = tencent;
    }

    @Override
    public List<SymbolInfo> fetchSymbolList(String type) {
        return sina.fetchList(type);
    }

    @Override
    public List<DailyBar> fetchDailyBars(String code, int market, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyBar> merged = new LinkedHashMap<>();
        LocalDate cursor = from;
        int guard = 0;

        while (!cursor.isAfter(to) && guard++ < MAX_SEGMENTS) {
            LocalDate segEnd = cursor.plusYears(SEGMENT_YEARS);
            if (segEnd.isAfter(to)) {
                segEnd = to;
            }
            // 请求失败向上抛，由同步任务计入失败并留待补跑；
            // 区间无数据属于正常情况（上市较晚），继续下一段。
            List<DailyBar> seg = tencent.fetchSegment(code, market, cursor, segEnd);
            for (DailyBar b : seg) {
                merged.put(b.getTradeDate(), b);
            }
            cursor = segEnd.plusDays(1);
        }

        List<DailyBar> bars = new ArrayList<>(merged.values());
        bars.sort(Comparator.comparing(DailyBar::getTradeDate));
        fillDerived(bars);
        return bars;
    }

    @Override
    public List<SymbolInfo> fetchQuotes(List<String> codes) {
        return tencent.fetchQuotes(codes);
    }

    /**
     * 上游只给开高低收量，涨跌幅与振幅在此补算。
     */
    private void fillDerived(List<DailyBar> bars) {
        for (int i = 1; i < bars.size(); i++) {
            DailyBar cur = bars.get(i);
            BigDecimal prevClose = bars.get(i - 1).getClose();
            if (prevClose == null || prevClose.doubleValue() <= 0) {
                continue;
            }
            double prev = prevClose.doubleValue();
            if (cur.getClose() != null) {
                cur.setPctChg(BigDecimal.valueOf((cur.getClose().doubleValue() - prev) / prev * 100)
                        .setScale(3, java.math.RoundingMode.HALF_UP));
            }
            if (cur.getHigh() != null && cur.getLow() != null) {
                cur.setAmplitude(BigDecimal.valueOf(
                                (cur.getHigh().doubleValue() - cur.getLow().doubleValue()) / prev * 100)
                        .setScale(3, java.math.RoundingMode.HALF_UP));
            }
        }
    }
}
