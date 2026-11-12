package com.quantlab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantlab.domain.entity.DailyBar;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyBarMapper extends BaseMapper<DailyBar> {

    /**
     * 批量写入，主键冲突时覆盖（保证重复同步幂等）。
     */
    @Insert("""
            <script>
            INSERT INTO daily_bar (code, trade_date, open, close, high, low, volume, amount, amplitude, pct_chg, turnover)
            VALUES
            <foreach collection="list" item="b" separator=",">
                (#{b.code}, #{b.tradeDate}, #{b.open}, #{b.close}, #{b.high}, #{b.low},
                 #{b.volume}, #{b.amount}, #{b.amplitude}, #{b.pctChg}, #{b.turnover})
            </foreach>
            ON DUPLICATE KEY UPDATE
                open = VALUES(open),
                close = VALUES(close),
                high = VALUES(high),
                low = VALUES(low),
                volume = VALUES(volume),
                amount = VALUES(amount),
                amplitude = VALUES(amplitude),
                pct_chg = VALUES(pct_chg),
                turnover = VALUES(turnover)
            </script>
            """)
    int batchUpsert(@Param("list") List<DailyBar> list);

    @Select("SELECT MAX(trade_date) FROM daily_bar WHERE code = #{code}")
    LocalDate selectMaxDate(@Param("code") String code);

    @Select("""
            SELECT code, trade_date, open, close, high, low, volume, amount, amplitude, pct_chg, turnover
            FROM daily_bar
            WHERE code = #{code} AND trade_date BETWEEN #{from} AND #{to}
            ORDER BY trade_date
            """)
    List<DailyBar> selectRange(@Param("code") String code,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    @Select("""
            SELECT code, trade_date, open, close, high, low, volume, amount, amplitude, pct_chg, turnover
            FROM daily_bar
            WHERE code = #{code}
            ORDER BY trade_date DESC
            LIMIT #{limit}
            """)
    List<DailyBar> selectLatest(@Param("code") String code, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM daily_bar WHERE code = #{code}")
    int countByCode(@Param("code") String code);

    @Select("SELECT MAX(trade_date) FROM daily_bar")
    LocalDate latestGlobalDate();
}
