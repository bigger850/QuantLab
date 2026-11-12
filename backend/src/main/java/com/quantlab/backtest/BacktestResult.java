package com.quantlab.backtest;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测输出。
 */
@Data
public class BacktestResult {

    private String code;
    private String name;
    private String strategy;
    private String strategyName;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private int barCount;

    private BigDecimal initCash;
    private BigDecimal finalEquity;
    /** 总收益率 % */
    private BigDecimal totalReturn;
    /** 年化收益率 % */
    private BigDecimal annualReturn;
    /** 最大回撤 % */
    private BigDecimal maxDrawdown;
    /** 夏普比率 */
    private BigDecimal sharpe;
    /** 胜率 % */
    private BigDecimal winRate;
    /** 盈亏比 */
    private BigDecimal profitFactor;
    /** 同期买入持有收益 % */
    private BigDecimal benchmarkReturn;

    private int tradeCount;
    private int winCount;
    private int loseCount;

    private List<EquityPoint> equityCurve;
    private List<TradeRecord> trades;
}
