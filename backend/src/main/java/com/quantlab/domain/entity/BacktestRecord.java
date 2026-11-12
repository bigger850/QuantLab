package com.quantlab.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("backtest_record")
public class BacktestRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String strategy;
    private String strategyName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal initCash;
    private BigDecimal finalEquity;
    private BigDecimal totalReturn;
    private BigDecimal annualReturn;
    private BigDecimal maxDrawdown;
    private BigDecimal sharpe;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private Integer tradeCount;
    private BigDecimal benchmarkReturn;
    private String paramsJson;
    private LocalDateTime createdAt;
}
