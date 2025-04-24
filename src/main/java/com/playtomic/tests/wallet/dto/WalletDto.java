package com.playtomic.tests.wallet.dto;

import com.playtomic.tests.wallet.entity.Wallet;

import java.math.BigDecimal;

public record WalletDto(Long id, Long userId, BigDecimal balance) {
    public static WalletDto from(Wallet wallet) {
        return new WalletDto(wallet.getId(), wallet.getUserId(), wallet.getBalance());
    }
}