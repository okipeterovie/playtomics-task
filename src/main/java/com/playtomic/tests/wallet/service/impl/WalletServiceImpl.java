package com.playtomic.tests.wallet.service.impl;

import com.playtomic.tests.wallet.dto.TopUpRequest;
import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.entity.Wallet;
import com.playtomic.tests.wallet.enums.TransactionStatus;
import com.playtomic.tests.wallet.enums.TransactionType;
import com.playtomic.tests.wallet.exceptions.PaymentRejectedException;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import com.playtomic.tests.wallet.repository.WalletRepository;
import com.playtomic.tests.wallet.service.Payment;
import com.playtomic.tests.wallet.service.StripeService;
import com.playtomic.tests.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

@Log4j2
@Service
public class WalletServiceImpl implements WalletService {

  private final WalletRepository walletRepository;
  private final TransactionRepository transactionRepository;
  private final StripeService stripeService;

  public WalletServiceImpl(WalletRepository walletRepository, TransactionRepository transactionRepository, StripeService stripeService) {
    this.walletRepository = walletRepository;
    this.transactionRepository = transactionRepository;
    this.stripeService = stripeService;
  }

  @Transactional
  public Transaction topUpWallet(Long walletId, TopUpRequest topUpRequest) {
    if (topUpRequest.amount().compareTo(BigDecimal.valueOf(0.01)) < 0) {
      throw new IllegalArgumentException("Amount must be greater than 0");
    }

    if (topUpRequest.cardNumber().isBlank()) {
      throw new IllegalArgumentException("Card number is required");
    }

    if (topUpRequest.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("Idempotency key is required");
    }

    // 1. Check for existing transaction (idempotency)
    transactionRepository.findByIdempotencyKey(topUpRequest.idempotencyKey())
        .ifPresent(existing -> {
          throw new IllegalStateException("Duplicate request");
        });

    // 2. Fetch wallet (will use @Version for optimistic locking)
    Wallet wallet = walletRepository.findById(walletId)
        .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));

    // 3. Create transaction record (PENDING)
    Transaction tx = new Transaction();
    tx.setWallet(wallet);
    tx.setAmount(topUpRequest.amount());
    tx.setType(TransactionType.TOPUP);
    tx.setStatus(TransactionStatus.PENDING);
    tx.setIdempotencyKey(topUpRequest.idempotencyKey());
    tx = transactionRepository.save(tx);

    try {
      // 4. Call payment gateway
      Payment payment = stripeService.charge(topUpRequest.cardNumber(), topUpRequest.amount());
      tx.setExternalReference(payment.getId());

      // 5. Update wallet balance
      wallet.setBalance(wallet.getBalance().add(topUpRequest.amount()));
      walletRepository.save(wallet);

      // 6. Mark transaction SUCCESS
      tx.setStatus(TransactionStatus.SUCCESS);
      return transactionRepository.save(tx);

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        tx.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(tx);
        throw new PaymentRejectedException("Payment rejected by processor");
      }

      tx.setStatus(TransactionStatus.FAILED);
      transactionRepository.save(tx);
      throw new RuntimeException("Payment service error: " + e.getMessage(), e);

    } catch (Exception e) {
      tx.setStatus(TransactionStatus.FAILED);
      transactionRepository.save(tx);
      throw new RuntimeException("Payment failed: " + e.getMessage());
    }
  }

  public Wallet getWallet(Long walletId) {
    return walletRepository.findById(walletId)
        .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
  }
}
