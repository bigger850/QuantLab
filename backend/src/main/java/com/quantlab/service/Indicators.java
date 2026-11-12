package com.quantlab.service;

import java.util.Arrays;

/**
 * 技术指标计算。全部基于原始数组，返回等长数组，
 * 无法计算的早期位置填 Double.NaN。
 */
public final class Indicators {

    private Indicators() {
    }

    /** 简单移动平均 */
    public static double[] sma(double[] src, int period) {
        double[] out = new double[src.length];
        Arrays.fill(out, Double.NaN);
        if (period <= 0 || src.length < period) {
            return out;
        }
        double sum = 0;
        for (int i = 0; i < src.length; i++) {
            sum += src[i];
            if (i >= period) {
                sum -= src[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    /** 指数移动平均 */
    public static double[] ema(double[] src, int period) {
        double[] out = new double[src.length];
        if (src.length == 0) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double prev = src[0];
        out[0] = prev;
        for (int i = 1; i < src.length; i++) {
            prev = src[i] * k + prev * (1 - k);
            out[i] = prev;
        }
        return out;
    }

    /** Wilder RSI，取值 0-100 */
    public static double[] rsi(double[] close, int period) {
        double[] out = new double[close.length];
        Arrays.fill(out, Double.NaN);
        if (close.length <= period || period <= 0) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = close[i] - close[i - 1];
            if (d > 0) {
                gain += d;
            } else {
                loss -= d;
            }
        }
        gain /= period;
        loss /= period;
        out[period] = loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);

        for (int i = period + 1; i < close.length; i++) {
            double d = close[i] - close[i - 1];
            double g = d > 0 ? d : 0;
            double l = d < 0 ? -d : 0;
            gain = (gain * (period - 1) + g) / period;
            loss = (loss * (period - 1) + l) / period;
            out[i] = loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);
        }
        return out;
    }

    /**
     * MACD。返回 {dif, dea, hist}，hist 为柱状值（2 x (dif-dea)）。
     */
    public static double[][] macd(double[] close, int fast, int slow, int signal) {
        double[] ef = ema(close, fast);
        double[] es = ema(close, slow);
        double[] dif = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            dif[i] = ef[i] - es[i];
        }
        double[] dea = ema(dif, signal);
        double[] hist = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            hist[i] = (dif[i] - dea[i]) * 2;
        }
        return new double[][]{dif, dea, hist};
    }

    /**
     * 布林带。返回 {upper, mid, lower}。
     */
    public static double[][] boll(double[] close, int period, double k) {
        int n = close.length;
        double[] mid = sma(close, period);
        double[] up = new double[n];
        double[] low = new double[n];
        Arrays.fill(up, Double.NaN);
        Arrays.fill(low, Double.NaN);
        for (int i = period - 1; i < n; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = close[j] - mid[i];
                sum += d * d;
            }
            double sd = Math.sqrt(sum / period);
            up[i] = mid[i] + k * sd;
            low[i] = mid[i] - k * sd;
        }
        return new double[][]{up, mid, low};
    }

    /** 平均真实波幅 */
    public static double[] atr(double[] high, double[] low, double[] close, int period) {
        int n = close.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (n < period || period <= 0) {
            return out;
        }
        double[] tr = new double[n];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < n; i++) {
            double a = high[i] - low[i];
            double b = Math.abs(high[i] - close[i - 1]);
            double c = Math.abs(low[i] - close[i - 1]);
            tr[i] = Math.max(a, Math.max(b, c));
        }
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += tr[i];
        }
        out[period - 1] = sum / period;
        for (int i = period; i < n; i++) {
            out[i] = (out[i - 1] * (period - 1) + tr[i]) / period;
        }
        return out;
    }

    /** 区间内的最大值位置，用于统计 */
    public static double maxDrawdown(double[] equity) {
        double peak = equity.length > 0 ? equity[0] : 0;
        double maxDd = 0;
        for (double v : equity) {
            if (v > peak) {
                peak = v;
            }
            if (peak > 0) {
                double dd = (peak - v) / peak;
                if (dd > maxDd) {
                    maxDd = dd;
                }
            }
        }
        return maxDd;
    }
}
