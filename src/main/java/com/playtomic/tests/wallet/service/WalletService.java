package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.entity.Wallet;

import java.math.BigDecimal;

public interface WalletService {
    Wallet getWallet(Long walletId);
    Transaction topUpWallet(Long walletId, BigDecimal amount, String cardNumber, String idempotencyKey);
}
