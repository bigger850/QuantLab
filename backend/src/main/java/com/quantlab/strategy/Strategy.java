package com.quantlab.strategy;

import com.quantlab.domain.entity.DailyBar;

import java.util.List;
import java.util.Map;

/**
 * 策略接口。
 * <p>
 * 约定：signals 返回每根K线的目标仓位比例（0.0~1.0），
 * Double.NaN 表示维持前一根的仓位不变。
 * 回测引擎读取第 i 根的 signal，在第 i+1 根开盘执行，避免未来函数。
 */
public interface Strategy {

    /** 策略唯一标识，与 strategy_meta 表一致 */
    String code();

    /** 展示名称 */
    String displayName();

    /**
     * 一次性计算整段信号序列，长度与 bars 相同。
     *
     * @param bars   完整K线（升序）
     * @param params 策略参数
     */
    double[] signals(List<DailyBar> bars, Map<String, Object> params);
}
