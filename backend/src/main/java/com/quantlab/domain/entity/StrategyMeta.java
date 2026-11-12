package com.quantlab.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("strategy_meta")
public class StrategyMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
    /** 参数定义 JSON 数组 */
    private String paramSchema;
    private Integer sortNo;
}
