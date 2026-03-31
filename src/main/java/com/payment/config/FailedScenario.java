package com.payment.config;

public enum FailedScenario {
    NO_REQUEST,
    NO_RESPONSE,
    UPSTREAM_429,
    UPSTREAM_5XX
}
