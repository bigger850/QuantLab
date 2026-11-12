package com.quantlab.backtest;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一笔成交记录。
 */
@Data
public class TradeRecord {

    private LocalDate date;
    /** BUY / SELL */
    private String side;
    private BigDecimal price;
    private BigDecimal qty;
    private BigDecimal amount;
    private BigDecimal fee;
    /** 平仓时的已实现盈亏，买入为 null */
    private BigDecimal pnl;
    /** 平仓时的收益率 % */
    private BigDecimal pnlPct;
}
