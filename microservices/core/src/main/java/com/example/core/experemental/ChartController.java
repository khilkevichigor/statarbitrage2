package com.example.core.experemental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChartController {
    private final AnalyzeChartIntersectionsService analyzeChartIntersectionsService;

    @GetMapping("/test-chart")
    public Map<String, Object> analyzeChart(
            @RequestParam String path,
            @RequestParam(defaultValue = "pixel") String mode,
            @RequestParam(defaultValue = "3") int distance) {
        return analyzeChartIntersectionsService.analyze(path, mode, distance);
    }
}
