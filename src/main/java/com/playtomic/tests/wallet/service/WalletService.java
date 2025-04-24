package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.dto.TopUpRequest;
import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.entity.Wallet;

public interface WalletService {
  Wallet getWallet(Long walletId);

  Transaction topUpWallet(Long walletId, TopUpRequest topUpRequest);
}
