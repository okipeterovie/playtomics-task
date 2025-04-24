package com.playtomic.tests.wallet.dto;

import java.math.BigDecimal;


public record TopUpRequest(BigDecimal amount, String cardNumber, String idempotencyKey) {}

