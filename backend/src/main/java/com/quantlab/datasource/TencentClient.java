package com.quantlab.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.BizException;
import com.quantlab.domain.entity.DailyBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 腾讯行情接口。
 * <p>
 * 日线：单次最多返回约 640 根，调用方按日期区间分段拉取。
 * 实时：返回 GBK 编码的文本行情，需按字节解码。
 */
@Slf4j
@Component
public class TencentClient {

    private static final String KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get";
    private static final String QUOTE_URL = "https://qt.gtimg.cn/q=";
    private static final Charset GBK = Charset.forName("GBK");

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long intervalMs;

    public TencentClient(@Qualifier("marketRestClient") RestClient client,
                         @Value("${quant.sync.request-interval-ms:60}") long intervalMs) {
        this.client = client;
        this.intervalMs = intervalMs;
    }

    /**
     * 拉取一段日线（前复权）。区间过长时上游会截断，调用方需分段。
     */
    public List<DailyBar> fetchSegment(String code, int market, LocalDate from, LocalDate to) {
        String symbol = symbolOf(code, market);
        String param = symbol + ",day," + from + "," + to + ",640,qfq";
        String url = KLINE_URL + "?param=" + param;

        JsonNode root = getJson(url, 2);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return List.of();
        }
        JsonNode node = data.path(symbol);
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        JsonNode klines = node.path("qfqday");
        if (!klines.isArray() || klines.isEmpty()) {
            klines = node.path("day");
        }
        if (!klines.isArray() || klines.isEmpty()) {
            return List.of();
        }

        List<DailyBar> bars = new ArrayList<>(klines.size());
        for (JsonNode k : klines) {
            if (!k.isArray() || k.size() < 6) {
                continue;
            }
            try {
                DailyBar b = new DailyBar();
                b.setCode(code);
                b.setTradeDate(LocalDate.parse(k.get(0).asText()));
                b.setOpen(new BigDecimal(k.get(1).asText()));
                b.setClose(new BigDecimal(k.get(2).asText()));
                b.setHigh(new BigDecimal(k.get(3).asText()));
                b.setLow(new BigDecimal(k.get(4).asText()));
                b.setVolume(k.get(5).asLong());
                // 腾讯日线不含成交额，用成交量(手) x 100 股 x 均价估算
                double avg = (b.getOpen().doubleValue() + b.getClose().doubleValue()) / 2;
                b.setAmount(BigDecimal.valueOf(b.getVolume() * 100 * avg));
                bars.add(b);
            } catch (Exception e) {
                log.debug("跳过异常K线 {} {}", code, k);
            }
        }
        return bars;
    }

    /**
     * 批量实时行情。每批最多 60 只。
     */
    public List<SymbolInfo> fetchQuotes(List<String> codes) {
        List<SymbolInfo> out = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return out;
        }
        for (int i = 0; i < codes.size(); i += 60) {
            List<String> slice = codes.subList(i, Math.min(i + 60, codes.size()));
            StringBuilder sb = new StringBuilder(QUOTE_URL);
            for (int j = 0; j < slice.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                String code = slice.get(j);
                sb.append(symbolOf(code, guessMarket(code)));
            }
            try {
                out.addAll(parseQuotes(fetchText(sb.toString())));
            } catch (Exception e) {
                log.warn("实时行情批次失败 offset={}: {}", i, e.getMessage());
            }
            throttle();
        }
        return out;
    }

    private List<SymbolInfo> parseQuotes(String text) {
        List<SymbolInfo> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        for (String line : text.split(";")) {
            String t = line.trim();
            if (t.isEmpty() || !t.startsWith("v_")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String payload = t.substring(eq + 1).replace("\"", "").trim();
            String[] f = payload.split("~");
            if (f.length < 33) {
                continue;
            }
            try {
                SymbolInfo s = new SymbolInfo();
                s.setName(f[1]);
                s.setCode(f[2]);
                s.setPrice(safeDec(f[3]));
                s.setPctChg(safeDec(f[32]));
                // 成交额字段单位为万元
                BigDecimal amountWan = safeDec(f[37]);
                if (amountWan != null) {
                    s.setTurnover(amountWan.multiply(BigDecimal.valueOf(10000)));
                }
                s.setMarket(f[2].startsWith("6") ? 1 : 0);
                list.add(s);
            } catch (Exception e) {
                log.debug("解析实时行情失败: {}", t.substring(0, Math.min(60, t.length())));
            }
        }
        return list;
    }

    // ------------------------------------------------------------

    private JsonNode getJson(String url, int maxRetry) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                String body = client.get().uri(URI.create(url)).retrieve().body(String.class);
                if (body == null || body.isBlank()) {
                    throw new IllegalStateException("空响应");
                }
                return mapper.readTree(body);
            } catch (Exception e) {
                last = new RuntimeException(e.getMessage(), e);
                // 指数退避：被限流时立即重试只会加重惩罚
                try {
                    Thread.sleep(Math.min(2000L, 300L * (1L << attempt)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new BizException(502, "腾讯行情请求失败: "
                + (last == null ? "unknown" : last.getMessage()));
    }

    private String fetchText(String url) {
        byte[] body = client.get().uri(URI.create(url)).retrieve().body(byte[].class);
        return body == null ? "" : new String(body, GBK);
    }

    private void throttle() {
        if (intervalMs > 0) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 生成腾讯格式的标的代码：sh600519 / sz000001 / bj920000。
     */
    public static String symbolOf(String code, int market) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        if (code.startsWith("92") || code.startsWith("4") || code.startsWith("8")) {
            return "bj" + code;
        }
        if (market == 1) {
            return "sh" + code;
        }
        return "sz" + code;
    }

    public static int guessMarket(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        char c = code.charAt(0);
        if (code.startsWith("92") || c == '4' || c == '8') {
            return 0;
        }
        return (c == '6' || c == '5') ? 1 : 0;
    }

    private static BigDecimal safeDec(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "-".equals(t) || "--".equals(t)) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
