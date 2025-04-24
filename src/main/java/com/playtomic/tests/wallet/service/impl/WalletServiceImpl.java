package com.playtomic.tests.wallet.service.impl;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

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
  public Transaction topUpWallet(Long walletId, BigDecimal amount, String cardNumber, String idempotencyKey) {
    // 1. Check for existing transaction (idempotency)
    transactionRepository.findByIdempotencyKey(idempotencyKey)
        .ifPresent(existing -> {
          throw new IllegalStateException("Duplicate request");
        });

    // 2. Fetch wallet (will use @Version for optimistic locking)
    Wallet wallet = walletRepository.findById(walletId)
        .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));

    // 3. Create transaction record (PENDING)
    Transaction tx = new Transaction();
    tx.setWallet(wallet);
    tx.setAmount(amount);
    tx.setType(TransactionType.TOPUP);
    tx.setStatus(TransactionStatus.PENDING);
    tx.setIdempotencyKey(idempotencyKey);
    tx = transactionRepository.save(tx);

    try {
      // 4. Call payment gateway
      Payment payment = stripeService.charge(cardNumber, amount);
      tx.setExternalReference(payment.getId());

      // 5. Update wallet balance
      wallet.setBalance(wallet.getBalance().add(amount));
      walletRepository.save(wallet);

      // 6. Mark transaction SUCCESS
      tx.setStatus(TransactionStatus.SUCCESS);
      return transactionRepository.save(tx);

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
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
