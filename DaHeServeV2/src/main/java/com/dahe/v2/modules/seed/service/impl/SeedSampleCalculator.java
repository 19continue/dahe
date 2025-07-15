package com.dahe.v2.modules.seed.service.impl;

import com.dahe.v2.modules.seed.model.SeedQualityRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * seed 检测样本计算器。
 */
@Component
public class SeedSampleCalculator {

    /**
     * 根据规则和请求样本数解析最终样本数。
     */
    public int resolveSampleCount(SeedQualityRule rule, Integer requestSampleCount) {
        int fixed = rule == null || rule.getFixedSampleSize() == null ? 1 : rule.getFixedSampleSize();
        int defaultCount = (rule == null || rule.getDefaultSampleSize() == null || rule.getDefaultSampleSize() <= 0)
                ? 100
                : rule.getDefaultSampleSize();
        if (fixed == 1) {
            return defaultCount;
        }
        return requestSampleCount == null ? defaultCount : requestSampleCount;
    }

    /**
     * 优先取发芽数；若未传则由发芽率按样本数反推。
     */
    public Integer resolveGerminationCount(Integer requestGerminationCount, Double requestGerminationRate, int sampleCount) {
        if (requestGerminationCount != null) {
            return requestGerminationCount;
        }
        if (requestGerminationRate == null) {
            return null;
        }
        double ratio = requestGerminationRate / 100.0;
        return (int) Math.round(sampleCount * ratio);
    }

    /**
     * 计算发芽率，保留两位小数。
     */
    public Double calcGerminationRate(Integer germinationCount, int sampleCount) {
        if (germinationCount == null || sampleCount <= 0) {
            return null;
        }
        BigDecimal rate = BigDecimal.valueOf(germinationCount * 100.0 / sampleCount).setScale(2, RoundingMode.HALF_UP);
        return rate.doubleValue();
    }
}

