package com.quantlab.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 新浪财经标的清单接口。
 * <p>
 * 全量 A 股与 ETF 各约 6000 / 1600 只，按 100 条一页翻。
 * 该接口需要 Referer，否则返回 403。
 */
@Slf4j
@Component
public class SinaClient {

    private static final String URL =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData";

    /** 新浪节点名到内部类型的映射 */
    private static final String NODE_STOCK = "hs_a";
    private static final String NODE_ETF = "etf_hq_fund";

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long intervalMs;

    public SinaClient(@org.springframework.beans.factory.annotation.Qualifier("marketRestClient") RestClient client,
                      @Value("${quant.sync.request-interval-ms:60}") long intervalMs) {
        this.client = client;
        this.intervalMs = intervalMs;
    }

    public List<SymbolInfo> fetchList(String type) {
        String node = switch (type) {
            case "ETF" -> NODE_ETF;
            case "STOCK" -> NODE_STOCK;
            default -> null;
        };
        if (node == null) {
            log.warn("新浪源不支持的类型: {}", type);
            return List.of();
        }

        List<SymbolInfo> all = new ArrayList<>();
        for (int page = 1; page <= 80; page++) {
            String url = URL + "?page=" + page + "&num=100&sort=symbol&asc=1&node=" + node;
            JsonNode arr = getJson(url);
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                break;
            }
            for (JsonNode n : arr) {
                String symbol = n.path("symbol").asText();
                String code = n.path("code").asText();
                if (code.isEmpty()) {
                    continue;
                }
                SymbolInfo s = new SymbolInfo();
                s.setCode(code);
                s.setMarket(marketOf(symbol));
                s.setName(n.path("name").asText());
                s.setType(type);
                s.setPrice(dec(n, "trade"));
                s.setPctChg(dec(n, "changepercent"));
                s.setTurnover(dec(n, "amount"));
                all.add(s);
            }
            if (arr.size() < 100) {
                break;
            }
            throttle();
        }
        log.info("新浪清单 {} 共 {} 条", type, all.size());
        return all;
    }

    private JsonNode getJson(String url) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                String body = client.get()
                        .uri(URI.create(url))
                        .header("Referer", "https://finance.sina.com.cn")
                        .retrieve()
                        .body(String.class);
                if (body == null || body.isBlank()) {
                    throw new IllegalStateException("空响应");
                }
                return mapper.readTree(body);
            } catch (Exception e) {
                last = new RuntimeException(e.getMessage(), e);
                throttle();
            }
        }
        throw new BizException(502, "新浪清单接口失败: "
                + (last == null ? "unknown" : last.getMessage()));
    }

    /** sh/sz/bj 前缀转内部市场标识：1=沪 0=深或北 */
    private static int marketOf(String symbol) {
        return symbol != null && symbol.startsWith("sh") ? 1 : 0;
    }

    private static java.math.BigDecimal dec(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        String t = v.asText().trim();
        if (t.isEmpty() || "-".equals(t)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
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
}
