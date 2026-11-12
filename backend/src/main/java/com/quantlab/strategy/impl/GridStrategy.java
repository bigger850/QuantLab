package com.quantlab.strategy.impl;

import com.quantlab.domain.entity.DailyBar;
import com.quantlab.strategy.Params;
import com.quantlab.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 网格交易：以首根收盘价为中枢，价格每下跌一格加一层仓位，
 * 每上涨一格减一层仓位。仓位上限由层数 x 单层仓位决定。
 */
@Component
public class GridStrategy implements Strategy {

    @Override
    public String code() {
        return "grid";
    }

    @Override
    public String displayName() {
        return "网格交易";
    }

    @Override
    public double[] signals(List<DailyBar> bars, Map<String, Object> params) {
        Params p = new Params(params);
        double stepPct = Math.max(0.5, p.getDouble("stepPct", 3.0));
        int grids = Math.max(1, p.getInt("grids", 5));
        double positionPct = Math.min(50, Math.max(1, p.getDouble("positionPct", 10))) / 100.0;

        int n = bars.size();
        double[] sig = new double[n];
        Arrays.fill(sig, Double.NaN);
        if (n == 0) {
            return sig;
        }

        double base = bars.get(0).getClose().doubleValue();
        double step = stepPct / 100.0;
        double maxPos = Math.min(1.0, grids * positionPct);

        for (int i = 0; i < n; i++) {
            double price = bars.get(i).getClose().doubleValue();
            double deviation = (price - base) / base;
            // 下跌为负：层数增大；上涨为正：层数减小
            int level = (int) Math.floor(-deviation / step);
            if (level > grids) {
                level = grids;
            }
            if (level < 0) {
                level = 0;
            }
            sig[i] = Math.min(maxPos, level * positionPct);
        }
        return sig;
    }
}
