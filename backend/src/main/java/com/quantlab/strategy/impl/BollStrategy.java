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
 * 布林带策略：站上中轨建仓，跌破下轨止损离场。
 */
@Component
public class BollStrategy implements Strategy {

    @Override
    public String code() {
        return "boll";
    }

    @Override
    public String displayName() {
        return "布林带突破";
    }

    @Override
    public double[] signals(List<DailyBar> bars, Map<String, Object> params) {
        Params p = new Params(params);
        int period = Math.max(5, p.getInt("period", 20));
        double dev = p.getDouble("dev", 2.0);

        int n = bars.size();
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = bars.get(i).getClose().doubleValue();
        }
        double[][] boll = Indicators.boll(close, period, dev);
        double[] mid = boll[1];
        double[] lower = boll[2];

        double[] sig = new double[n];
        Arrays.fill(sig, Double.NaN);
        double pos = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(mid[i]) || Double.isNaN(lower[i])) {
                continue;
            }
            if (close[i] > mid[i]) {
                pos = 1.0;
            } else if (close[i] < lower[i]) {
                pos = 0.0;
            }
            sig[i] = pos;
        }
        return sig;
    }
}
