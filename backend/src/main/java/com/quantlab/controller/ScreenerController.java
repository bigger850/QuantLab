package com.quantlab.controller;

import com.quantlab.common.ApiResponse;
import com.quantlab.dto.ScreenerItem;
import com.quantlab.dto.ScreenerRequest;
import com.quantlab.service.ScreenerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/screener")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @PostMapping("/run")
    public ApiResponse<List<ScreenerItem>> run(@RequestBody ScreenerRequest req) {
        long begin = System.currentTimeMillis();
        List<ScreenerItem> items = screenerService.screen(req);
        return ApiResponse.ok(items);
    }
}
