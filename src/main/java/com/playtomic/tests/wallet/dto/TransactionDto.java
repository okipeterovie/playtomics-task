package com.playtomic.tests.wallet.dto;

import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.enums.TransactionStatus;
import com.playtomic.tests.wallet.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionDto(Long id, TransactionType type, BigDecimal amount, TransactionStatus status,
                             String reference) {
  public static TransactionDto from(Transaction tx) {
    return new TransactionDto(tx.getId(), tx.getType(), tx.getAmount(), tx.getStatus(), tx.getExternalReference());
  }
}