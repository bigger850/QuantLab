package com.quantlab.service;

import com.quantlab.domain.entity.DailyBar;
import com.quantlab.domain.entity.Stock;
import com.quantlab.dto.KlineView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装 K 线 + 指标的视图。
 */
@Service
public class KlineViewService {

    private final QuoteService quoteService;
    private final StockService stockService;

    public KlineViewService(QuoteService quoteService, StockService stockService) {
        this.quoteService = quoteService;
        this.stockService = stockService;
    }

    public KlineView build(String code, int limit, boolean withIndicators) {
        Stock stock = stockService.getByCode(code);
        List<DailyBar> bars = quoteService.getKline(code, limit, true);

        KlineView view = new KlineView();
        view.setCode(stock.getCode());
        view.setName(stock.getName());
        view.setType(stock.getType());
        view.setLatestPrice(stock.getLatestPrice());
        view.setPctChg(stock.getPctChg());

        List<KlineView.Bar> list = new ArrayList<>(bars.size());
        for (DailyBar b : bars) {
            KlineView.Bar bar = new KlineView.Bar();
            bar.setDate(b.getTradeDate().toString());
            bar.setOpen(b.getOpen());
            bar.setClose(b.getClose());
            bar.setHigh(b.getHigh());
            bar.setLow(b.getLow());
            bar.setVolume(b.getVolume());
            bar.setAmount(b.getAmount());
            bar.setPctChg(b.getPctChg());
            list.add(bar);
        }
        view.setBars(list);

        if (withIndicators && !bars.isEmpty()) {
            view.setIndicators(computeIndicators(bars));
        }
        return view;
    }

    private Map<String, Double[]> computeIndicators(List<DailyBar> bars) {
        int n = bars.size();
        double[] close = new double[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] volume = new double[n];
        for (int i = 0; i < n; i++) {
            DailyBar b = bars.get(i);
            close[i] = b.getClose().doubleValue();
            high[i] = b.getHigh().doubleValue();
            low[i] = b.getLow().doubleValue();
            volume[i] = b.getVolume() == null ? 0 : b.getVolume().doubleValue();
        }

        Map<String, Double[]> m = new LinkedHashMap<>();
        m.put("ma5", box(Indicators.sma(close, 5)));
        m.put("ma10", box(Indicators.sma(close, 10)));
        m.put("ma20", box(Indicators.sma(close, 20)));
        m.put("ma60", box(Indicators.sma(close, 60)));

        double[][] macd = Indicators.macd(close, 12, 26, 9);
        m.put("dif", box(macd[0]));
        m.put("dea", box(macd[1]));
        m.put("macd", box(macd[2]));

        m.put("rsi6", box(Indicators.rsi(close, 6)));
        m.put("rsi14", box(Indicators.rsi(close, 14)));

        double[][] boll = Indicators.boll(close, 20, 2);
        m.put("bollUp", box(boll[0]));
        m.put("bollMid", box(boll[1]));
        m.put("bollLow", box(boll[2]));

        m.put("volMa5", box(Indicators.sma(volume, 5)));
        return m;
    }

    /**
     * NaN 不能出现在 JSON 里，统一转 null 并保留三位小数。
     */
    private static Double[] box(double[] arr) {
        Double[] out = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            double v = arr[i];
            out[i] = (Double.isNaN(v) || Double.isInfinite(v))
                    ? null
                    : Math.round(v * 1000) / 1000.0;
        }
        return out;
    }
}
