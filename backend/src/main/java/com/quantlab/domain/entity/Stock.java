package com.quantlab.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    /** 0=深 1=沪 */
    private Integer market;
    private String name;
    /** STOCK / ETF / INDEX */
    private String type;
    private String industry;
    private BigDecimal latestPrice;
    private BigDecimal pctChg;
    private BigDecimal turnover;
    private Integer barCount;
    private LocalDate lastBarDate;
    private Integer status;
    private LocalDateTime updatedAt;
}
