package com.quantlab.strategy;

import com.quantlab.common.BizException;
import com.quantlab.strategy.impl.BollStrategy;
import com.quantlab.strategy.impl.GridStrategy;
import com.quantlab.strategy.impl.MaCrossStrategy;
import com.quantlab.strategy.impl.MomentumStrategy;
import com.quantlab.strategy.impl.RsiStrategy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略注册表。新增策略只需实现 Strategy 并交给 Spring 管理。
 */
@Component
public class StrategyRegistry {

    private final Map<String, Strategy> strategies = new LinkedHashMap<>();

    public StrategyRegistry(List<Strategy> beans) {
        for (Strategy s : beans) {
            strategies.put(s.code(), s);
        }
    }

    public Strategy get(String code) {
        Strategy s = strategies.get(code);
        if (s == null) {
            throw new BizException("未知策略: " + code);
        }
        return s;
    }

    public List<Map<String, String>> listBrief() {
        return strategies.values().stream()
                .map(s -> Map.of("code", s.code(), "name", s.displayName()))
                .toList();
    }

    public Map<String, Strategy> all() {
        return strategies;
    }

    @SuppressWarnings("unused")
    private static Class<?>[] builtin() {
        return new Class<?>[]{MaCrossStrategy.class, MomentumStrategy.class,
                RsiStrategy.class, BollStrategy.class, GridStrategy.class};
    }
}
