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
 * RSI 超买超卖：跌破超卖线建仓，升破超买线离场，中间区间维持原仓位。
 */
@Component
public class RsiStrategy implements Strategy {

    @Override
    public String code() {
        return "rsi";
    }

    @Override
    public String displayName() {
        return "RSI 超买超卖";
    }

    @Override
    public double[] signals(List<DailyBar> bars, Map<String, Object> params) {
        Params p = new Params(params);
        int period = Math.max(2, p.getInt("period", 14));
        double oversold = p.getDouble("oversold", 30);
        double overbought = p.getDouble("overbought", 70);

        int n = bars.size();
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = bars.get(i).getClose().doubleValue();
        }
        double[] rsi = Indicators.rsi(close, period);

        double[] sig = new double[n];
        Arrays.fill(sig, Double.NaN);
        double pos = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(rsi[i])) {
                continue;
            }
            if (rsi[i] <= oversold) {
                pos = 1.0;
            } else if (rsi[i] >= overbought) {
                pos = 0.0;
            }
            sig[i] = pos;
        }
        return sig;
    }
}
