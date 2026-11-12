package com.quantlab.strategy.impl;

import com.quantlab.domain.entity.DailyBar;
import com.quantlab.strategy.Params;
import com.quantlab.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 动量策略：过去 N 日涨幅超过阈值时买入，回撤超过止损线离场。
 */
@Component
public class MomentumStrategy implements Strategy {

    @Override
    public String code() {
        return "momentum";
    }

    @Override
    public String displayName() {
        return "动量轮动";
    }

    @Override
    public double[] signals(List<DailyBar> bars, Map<String, Object> params) {
        Params p = new Params(params);
        int lookback = Math.max(5, p.getInt("lookback", 20));
        double threshold = p.getDouble("threshold", 5.0);
        double stopLoss = p.getDouble("stopLoss", 8.0);

        int n = bars.size();
        double[] sig = new double[n];
        Arrays.fill(sig, Double.NaN);

        double entryPrice = 0;
        boolean holding = false;

        for (int i = lookback; i < n; i++) {
            double now = bars.get(i).getClose().doubleValue();
            double past = bars.get(i - lookback).getClose().doubleValue();
            if (past <= 0) {
                continue;
            }
            double gainPct = (now - past) / past * 100;

            if (!holding) {
                if (gainPct >= threshold) {
                    holding = true;
                    entryPrice = now;
                    sig[i] = 1.0;
                } else {
                    sig[i] = 0.0;
                }
            } else {
                double drawdown = (entryPrice - now) / entryPrice * 100;
                if (drawdown >= stopLoss || gainPct < 0) {
                    holding = false;
                    sig[i] = 0.0;
                } else {
                    sig[i] = 1.0;
                }
            }
        }
        return sig;
    }
}
