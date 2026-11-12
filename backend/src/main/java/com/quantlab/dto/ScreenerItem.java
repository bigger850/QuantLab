package com.quantlab.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 筛选结果中的单只标的。
 */
@Data
public class ScreenerItem {

    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal pctChg;
    private BigDecimal amount;
    private BigDecimal return5;
    private BigDecimal return20;
    private BigDecimal return60;
    private BigDecimal volatility20;
    private BigDecimal drawdown60;
    private BigDecimal ma20;
    /** 收盘价相对 20 日线的偏离 % */
    private BigDecimal ma20Deviation;
    private LocalDate lastDate;
}
