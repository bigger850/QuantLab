package com.quantlab.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 选股条件。所有字段均为可选，不传表示不限制。
 */
@Data
public class ScreenerRequest {

    /** 标的类型 STOCK/ETF，默认 STOCK */
    private String type = "STOCK";
    /** 候选池大小，按成交额降序取前 N 只 */
    private int poolSize = 300;
    /** 成交额下限（元） */
    private BigDecimal minAmount;
    /** 20 日涨幅下限 % */
    private BigDecimal minReturn20;
    /** 20 日涨幅上限 % */
    private BigDecimal maxReturn20;
    /** 20 日年化波动率上限 % */
    private BigDecimal maxVolatility;
    /** 距 60 日高点回撤上限 % */
    private BigDecimal maxDrawdown60;
    /** 是否要求收盘价站上 20 日均线 */
    private Boolean aboveMa20;
    /** 结果数量上限 */
    private int limit = 50;
}
