package com.quantlab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantlab.domain.entity.BacktestRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BacktestRecordMapper extends BaseMapper<BacktestRecord> {
}
