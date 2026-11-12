package com.quantlab.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class BacktestRequest {

    private String code;
    private String strategy = "ma_cross";
    private Map<String, Object> params;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 初始资金。高价股一手十几万，默认给足 */
    private BigDecimal initCash = BigDecimal.valueOf(1000000);
    /** 是否落库保存记录 */
    private boolean save = true;
}
