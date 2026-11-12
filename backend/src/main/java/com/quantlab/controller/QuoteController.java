package com.quantlab.controller;

import com.quantlab.common.ApiResponse;
import com.quantlab.service.QuoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quote")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * 批量实时行情，codes 用逗号分隔。
     */
    @GetMapping("/realtime")
    public ApiResponse<Map<String, Object>> realtime(@RequestParam String codes) {
        List<String> list = Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(100)
                .toList();
        return ApiResponse.ok(quoteService.realtime(list));
    }
}
