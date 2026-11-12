package com.quantlab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantlab.domain.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Select("SELECT COUNT(*) FROM stock WHERE bar_count > 0")
    int countWithBars();

    @Select("""
            SELECT
                SUM(CASE WHEN pct_chg > 0 THEN 1 ELSE 0 END) AS upCount,
                SUM(CASE WHEN pct_chg < 0 THEN 1 ELSE 0 END) AS downCount,
                SUM(CASE WHEN pct_chg = 0 THEN 1 ELSE 0 END) AS flatCount
            FROM stock
            WHERE type = 'STOCK' AND status = 1 AND pct_chg IS NOT NULL
            """)
    List<java.util.Map<String, Object>> marketBreadth();

    @Select("SELECT code FROM stock WHERE status = 1 AND type = #{type}")
    List<String> selectCodesByType(String type);
}
