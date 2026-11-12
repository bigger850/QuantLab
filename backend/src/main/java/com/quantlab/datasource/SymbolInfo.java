package com.quantlab.datasource;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 标的快照信息，同时用于列表同步与实时行情。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymbolInfo {

    private String code;
    /** 0=深 1=沪 */
    private Integer market;
    private String name;
    /** STOCK / ETF / INDEX */
    private String type;
    private BigDecimal price;
    private BigDecimal pctChg;
    private BigDecimal turnover;
}
