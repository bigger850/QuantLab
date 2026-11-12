package com.quantlab.strategy.impl;

import com.quantlab.domain.entity.DailyBar;
import com.quantlab.service.Indicators;
import com.quantlab.strategy.Params;
import com.quantlab.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 双均线交叉：快线在慢线之上持满仓，之下清仓。
 */
@Component
public class MaCrossStrategy implements Strategy {

    @Override
    public String code() {
        return "ma_cross";
    }

    @Override
    public String displayName() {
        return "双均线交叉";
    }

    @Override
    public double[] signals(List<DailyBar> bars, Map<String, Object> params) {
        Params p = new Params(params);
        int fast = Math.max(2, p.getInt("fast", 5));
        int slow = Math.max(fast + 1, p.getInt("slow", 20));

        int n = bars.size();
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = bars.get(i).getClose().doubleValue();
        }
        double[] maFast = Indicators.sma(close, fast);
        double[] maSlow = Indicators.sma(close, slow);

        double[] sig = new double[n];
        Arrays.fill(sig, Double.NaN);
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(maFast[i]) || Double.isNaN(maSlow[i])) {
                continue;
            }
            sig[i] = maFast[i] > maSlow[i] ? 1.0 : 0.0;
        }
        return sig;
    }
}
