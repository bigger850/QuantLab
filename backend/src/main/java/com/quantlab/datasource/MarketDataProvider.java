package com.quantlab.datasource;

import com.quantlab.domain.entity.DailyBar;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情数据源抽象。更换或新增数据源时只实现这一层，业务代码不动。
 */
public interface MarketDataProvider {

    /**
     * 拉取某类标的清单。
     *
     * @param type STOCK / ETF / INDEX
     */
    List<SymbolInfo> fetchSymbolList(String type);

    /**
     * 拉取单只标的的日线（前复权），按时间升序。
     * 区间由调用方决定，实现负责分段请求上游。
     */
    List<DailyBar> fetchDailyBars(String code, int market, LocalDate from, LocalDate to);

    /**
     * 批量拉取实时行情快照。
     */
    List<SymbolInfo> fetchQuotes(List<String> codes);
}
