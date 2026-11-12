package com.quantlab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantlab.domain.entity.Watchlist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WatchlistMapper extends BaseMapper<Watchlist> {
}
