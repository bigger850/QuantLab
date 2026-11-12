package com.quantlab.backtest;

import com.quantlab.domain.entity.DailyBar;
import com.quantlab.service.Indicators;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 单标的回测引擎。
 * <p>
 * 规则：
 * 1. 第 i-1 根收盘产生信号，第 i 根开盘价成交，杜绝未来函数；
 * 2. 佣金万 2.5、单笔最低 5 元，卖出另收万 5 印花税；
 * 3. 买入按 100 股整手，清仓时零碎股一并卖出；
 * 4. 未平仓部分按最后收盘价估值，不强制平仓。
 */
@Slf4j
@Component
public class BacktestEngine {

    private static final double COMMISSION_RATE = 0.00025;
    private static final double MIN_COMMISSION = 5.0;
    private static final double STAMP_TAX_RATE = 0.0005;
    private static final int LOT_SIZE = 100;
    private static final double RISK_FREE_RATE = 0.02;
    private static final int TRADING_DAYS_PER_YEAR = 252;

    public BacktestResult run(String code, String name,
                              String strategyCode, String strategyName,
                              List<DailyBar> bars, double[] rawSignals,
                              BigDecimal initCash) {
        BacktestResult result = new BacktestResult();
        result.setCode(code);
        result.setName(name);
        result.setStrategy(strategyCode);
        result.setStrategyName(strategyName);
        result.setInitCash(initCash);

        int n = bars.size();
        result.setBarCount(n);
        if (n < 2) {
            fillEmpty(result, initCash);
            return result;
        }

        double[] signals = rawSignals;
        if (signals == null || signals.length != n) {
            signals = new double[n];
            java.util.Arrays.fill(signals, Double.NaN);
        }

        double cash = initCash.doubleValue();
        double shares = 0;
        double costBasis = 0;

        double[] equity = new double[n];
        double[] benchEquity = new double[n];
        double firstOpen = bars.get(0).getOpen().doubleValue();
        equity[0] = cash;
        benchEquity[0] = cash;

        List<TradeRecord> trades = new ArrayList<>();
        int winCount = 0;
        int loseCount = 0;
        double grossProfit = 0;
        double grossLoss = 0;

        for (int i = 1; i < n; i++) {
            DailyBar bar = bars.get(i);
            double open = bar.getOpen().doubleValue();

            if (open > 0) {
                double pos = signals[i - 1];
                if (!Double.isNaN(pos)) {
                    pos = Math.max(0, Math.min(1, pos));
                    double totalEquity = cash + shares * open;
                    double targetQty = totalEquity * pos / open;

                    if (targetQty > shares) {
                        double need = (targetQty - shares) * open;
                        double budget = Math.min(need, cash);
                        if (budget >= open * LOT_SIZE + MIN_COMMISSION) {
                            double feeGuess = Math.max(budget * COMMISSION_RATE, MIN_COMMISSION);
                            double usable = budget - feeGuess;
                            int lots = (int) (usable / (open * LOT_SIZE));
                            if (lots > 0) {
                                double qty = (double) lots * LOT_SIZE;
                                double amount = qty * open;
                                double fee = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
                                cash -= amount + fee;
                                shares += qty;
                                costBasis += amount + fee;
                                trades.add(buildTrade(bar.getTradeDate(), "BUY", open, qty, amount, fee, null));
                            }
                        }
                    } else if (targetQty < shares) {
                        double qty;
                        if (pos <= 0.0001) {
                            qty = shares;
                        } else {
                            int lots = (int) ((shares - targetQty) / LOT_SIZE);
                            qty = (double) lots * LOT_SIZE;
                        }
                        if (qty > 0 && shares > 0) {
                            double amount = qty * open;
                            double fee = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION)
                                    + amount * STAMP_TAX_RATE;
                            double costPart = costBasis * (qty / shares);
                            double pnl = amount - fee - costPart;
                            cash += amount - fee;
                            shares -= qty;
                            costBasis -= costPart;

                            if (pnl > 0) {
                                winCount++;
                                grossProfit += pnl;
                            } else {
                                loseCount++;
                                grossLoss += Math.abs(pnl);
                            }
                            TradeRecord t = buildTrade(bar.getTradeDate(), "SELL", open, qty, amount, fee, pnl);
                            if (costPart > 0) {
                                t.setPnlPct(round(pnl / costPart * 100));
                            }
                            trades.add(t);
                        }
                    }
                }
            }

            double close = bar.getClose().doubleValue();
            equity[i] = cash + shares * close;
            benchEquity[i] = firstOpen > 0 ? initCash.doubleValue() * close / firstOpen : initCash.doubleValue();
        }

        double finalEquity = equity[n - 1];
        double totalReturn = (finalEquity - initCash.doubleValue()) / initCash.doubleValue() * 100;
        double benchReturn = firstOpen > 0
                ? (bars.get(n - 1).getClose().doubleValue() - firstOpen) / firstOpen * 100
                : 0;

        double years = (double) n / TRADING_DAYS_PER_YEAR;
        double annual = years > 0
                ? (Math.pow(Math.max(finalEquity, 0.0001) / initCash.doubleValue(), 1.0 / years) - 1) * 100
                : 0;

        double mdd = Indicators.maxDrawdown(equity) * 100;
        double[] daily = new double[n - 1];
        for (int i = 1; i < n; i++) {
            daily[i - 1] = equity[i - 1] > 0 ? equity[i] / equity[i - 1] - 1 : 0;
        }
        double sharpe = sharpe(daily);

        int closed = winCount + loseCount;
        double winRate = closed > 0 ? (double) winCount / closed * 100 : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 99.99 : 0);

        result.setStartDate(bars.get(0).getTradeDate());
        result.setEndDate(bars.get(n - 1).getTradeDate());
        result.setFinalEquity(round2(finalEquity));
        result.setTotalReturn(round(totalReturn));
        result.setAnnualReturn(round(annual));
        result.setMaxDrawdown(round(mdd));
        result.setSharpe(round(sharpe));
        result.setWinRate(round(winRate));
        result.setProfitFactor(round(profitFactor));
        result.setBenchmarkReturn(round(benchReturn));
        result.setTradeCount(trades.size());
        result.setWinCount(winCount);
        result.setLoseCount(loseCount);

        List<EquityPoint> curve = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double peak = 0;
            for (int j = 0; j <= i; j++) {
                if (equity[j] > peak) {
                    peak = equity[j];
                }
            }
            double dd = peak > 0 ? (peak - equity[i]) / peak * 100 : 0;
            curve.add(new EquityPoint(bars.get(i).getTradeDate(),
                    round2(equity[i]), round2(benchEquity[i]), round(dd)));
        }
        result.setEquityCurve(curve);
        result.setTrades(trades);
        return result;
    }

    // ------------------------------------------------------------

    private static double sharpe(double[] dailyReturns) {
        if (dailyReturns.length < 2) {
            return 0;
        }
        double sum = 0;
        for (double r : dailyReturns) {
            sum += r;
        }
        double mean = sum / dailyReturns.length;
        double var = 0;
        for (double r : dailyReturns) {
            var += (r - mean) * (r - mean);
        }
        var /= (dailyReturns.length - 1);
        double sd = Math.sqrt(var);
        if (sd == 0) {
            return 0;
        }
        double excess = mean - RISK_FREE_RATE / TRADING_DAYS_PER_YEAR;
        return excess / sd * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    private static TradeRecord buildTrade(java.time.LocalDate date, String side, double price,
                                          double qty, double amount, double fee, Double pnl) {
        TradeRecord t = new TradeRecord();
        t.setDate(date);
        t.setSide(side);
        t.setPrice(round2(price));
        t.setQty(round2(qty));
        t.setAmount(round2(amount));
        t.setFee(round2(fee));
        if (pnl != null) {
            t.setPnl(round2(pnl));
        }
        return t;
    }

    private static void fillEmpty(BacktestResult r, BigDecimal initCash) {
        r.setFinalEquity(initCash);
        r.setTotalReturn(BigDecimal.ZERO);
        r.setAnnualReturn(BigDecimal.ZERO);
        r.setMaxDrawdown(BigDecimal.ZERO);
        r.setSharpe(BigDecimal.ZERO);
        r.setWinRate(BigDecimal.ZERO);
        r.setProfitFactor(BigDecimal.ZERO);
        r.setBenchmarkReturn(BigDecimal.ZERO);
        r.setTradeCount(0);
        r.setEquityCurve(List.of());
        r.setTrades(List.of());
    }

    private static BigDecimal round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal round2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
