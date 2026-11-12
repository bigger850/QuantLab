package com.quantlab.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * K线视图，含原始行情与技术指标。
 * 指标数组与 bars 等长，无法计算的早期位置为 null。
 */
@Data
public class KlineView {

    private String code;
    private String name;
    private String type;
    private BigDecimal latestPrice;
    private BigDecimal pctChg;

    private List<Bar> bars;
    /** 指标序列，与 bars 等长，早期无法计算的位置为 null */
    private Map<String, Double[]> indicators;

    @Data
    public static class Bar {
        private String date;
        private BigDecimal open;
        private BigDecimal close;
        private BigDecimal high;
        private BigDecimal low;
        private Long volume;
        private BigDecimal amount;
        private BigDecimal pctChg;
    }
}
