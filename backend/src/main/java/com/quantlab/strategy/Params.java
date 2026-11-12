package com.quantlab.strategy;

import java.util.Map;

/**
 * 策略参数读取工具，带默认值与类型兜底。
 */
public class Params {

    private final Map<String, Object> raw;

    public Params(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public int getInt(String key, int def) {
        Object v = raw.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        Object v = raw.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Map<String, Object> asMap() {
        return raw;
    }
}
