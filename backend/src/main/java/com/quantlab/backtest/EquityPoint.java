package com.quantlab.backtest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 净值曲线上的一个点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquityPoint {

    private LocalDate date;
    /** 策略权益 */
    private BigDecimal equity;
    /** 同期买入持有基准权益 */
    private BigDecimal benchmark;
    /** 当前回撤 %（正数） */
    private BigDecimal drawdown;
}
