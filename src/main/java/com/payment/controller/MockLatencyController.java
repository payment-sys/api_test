package com.payment.controller;

import com.payment.service.mock.MockLatencyControllerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/latency")
@RequiredArgsConstructor
public class MockLatencyController {
    private final MockLatencyControllerService mockLatencyControllerService;

    @PostMapping
    public LatencyRes overrideLatency(@RequestBody LatencyReq req) {
        return new LatencyRes(mockLatencyControllerService.override(req.latencyMs()));
    }

    @DeleteMapping
    public LatencyRes clearOverride() {
        return new LatencyRes(mockLatencyControllerService.clearOverride());
    }

    public record LatencyReq(long latencyMs) {
    }

    public record LatencyRes(long latencyMs) {
    }
}
