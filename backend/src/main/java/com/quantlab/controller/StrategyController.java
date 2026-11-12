package com.quantlab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantlab.common.ApiResponse;
import com.quantlab.domain.entity.StrategyMeta;
import com.quantlab.mapper.StrategyMetaMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略元数据，供前端渲染参数表单。
 */
@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    private final StrategyMetaMapper mapper;

    public StrategyController(StrategyMetaMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<StrategyMeta> metas = mapper.selectList(
                new LambdaQueryWrapper<StrategyMeta>().orderByAsc(StrategyMeta::getSortNo));
        List<Map<String, Object>> out = metas.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", m.getCode());
            item.put("name", m.getName());
            item.put("description", m.getDescription());
            item.put("paramSchema", m.getParamSchema());
            return item;
        }).toList();
        return ApiResponse.ok(out);
    }
}
