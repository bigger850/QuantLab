package com.quantlab.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日线行情。复合主键 (code, trade_date)，不做单条按 ID 操作。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("daily_bar")
public class DailyBar {

    private String code;
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal amplitude;
    private BigDecimal pctChg;
    private BigDecimal turnover;
}
