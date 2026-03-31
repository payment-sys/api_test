package com.payment.controller.dto;

public sealed interface Result permits SuccessResult, FailedResult{
}
