package com.playtomic.tests.wallet.service.impl;

import com.playtomic.tests.wallet.dto.TopUpRequest;
import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.entity.Wallet;
import com.playtomic.tests.wallet.exceptions.PaymentRejectedException;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import com.playtomic.tests.wallet.repository.WalletRepository;
import com.playtomic.tests.wallet.service.Payment;
import com.playtomic.tests.wallet.service.StripeService;
import com.playtomic.tests.wallet.service.WalletService;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Log4j2
class WalletServiceTest {

  private WalletRepository walletRepository;
  private TransactionRepository transactionRepository;
  private StripeService stripeService;
  private WalletService walletService;

  @BeforeEach
  void setUp() {
    walletRepository = mock(WalletRepository.class);
    transactionRepository = mock(TransactionRepository.class);
    stripeService = mock(StripeService.class);

    walletService = new WalletServiceImpl(walletRepository, transactionRepository, stripeService);
  }

  @Test
  void getWallet_shouldReturnWallet() {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setBalance(BigDecimal.TEN);

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

    Wallet result = walletService.getWallet(1L);

    assertEquals(BigDecimal.TEN, result.getBalance());
  }

  @Test
  void topUp_shouldThrow_whenAmountIsInvalid() {
    TopUpRequest request = new TopUpRequest(BigDecimal.ZERO, "4111111111111111", "key-123");

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
        walletService.topUpWallet(1L, request));

    assertEquals("Amount must be greater than 0", exception.getMessage());
  }

  @Test
  void topUp_shouldThrow_whenCardNumberIsBlank() {
    TopUpRequest request = new TopUpRequest(BigDecimal.TEN, "  ", "key-123");

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
        walletService.topUpWallet(1L, request));

    assertEquals("Card number is required", exception.getMessage());
  }

  @Test
  void topUp_shouldThrow_whenIdempotencyKeyIsBlank() {
    TopUpRequest request = new TopUpRequest(BigDecimal.TEN, "4111111111111111", "");

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
        walletService.topUpWallet(1L, request));

    assertEquals("Idempotency key is required", exception.getMessage());
  }

  @Test
  void topUp_shouldIncreaseBalanceAndCreateTransaction() {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setBalance(BigDecimal.ZERO);

    TopUpRequest request = new TopUpRequest(
        BigDecimal.valueOf(100),
        "4111111111111111",
        "key-abc"
    );

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
    when(stripeService.charge(anyString(), any())).thenReturn(new Payment("stripe-ref"));
    when(transactionRepository.findByIdempotencyKey("key-abc")).thenReturn(Optional.empty());
    when(transactionRepository.save(any())).thenReturn(new Transaction());

    walletService.topUpWallet(1L, request);

    assertEquals(BigDecimal.valueOf(100), wallet.getBalance());
    verify(transactionRepository, times(2)).save(any());
  }

  @Test
  void topUp_shouldThrowWhenPaymentRejected() {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setBalance(BigDecimal.ZERO);

    TopUpRequest request = new TopUpRequest(
        BigDecimal.valueOf(100),
        "4111111111111111",
        "fail-key"
    );

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findByIdempotencyKey("fail-key")).thenReturn(Optional.empty());
    when(stripeService.charge(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));
    when(transactionRepository.save(any())).thenReturn(new Transaction());

    assertThrows(PaymentRejectedException.class, () -> walletService.topUpWallet(1L, request));
  }

  @Test
  void topUp_shouldSkipIfIdempotencyKeyExists() {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setBalance(BigDecimal.ZERO);

    TopUpRequest request = new TopUpRequest(
        BigDecimal.valueOf(50),
        "4111111111111111",
        "exists"
    );

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findByIdempotencyKey("exists")).thenReturn(Optional.of(new Transaction()));

    assertThrows(IllegalStateException.class, () -> walletService.topUpWallet(1L, request));

    assertEquals(BigDecimal.ZERO, wallet.getBalance()); // no change
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void topUp_shouldHandleConcurrentAccessWithOptimisticLock() throws InterruptedException {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setBalance(BigDecimal.ZERO);

    TopUpRequest request = new TopUpRequest(
        BigDecimal.valueOf(100),
        "4111111111111111",
        "concurrent-key");

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
    when(transactionRepository.findByIdempotencyKey("concurrent-key")).thenReturn(Optional.empty());
    when(stripeService.charge(anyString(), any())).thenReturn(new Payment("stripe-concurrent"));

    // Simulate one call succeeding, and the second failing due to version conflict
    when(transactionRepository.save(any())).thenAnswer(invocation -> null);
    when(walletRepository.save(any()))
        .thenAnswer(invocation -> {
          Wallet w = invocation.getArgument(0);
          if (w.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new OptimisticLockingFailureException("Conflict");
          }
          return w;
        });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(2);

    Runnable task = () -> {
      try {
        walletService.topUpWallet(1L, request);
      } catch (Exception ignored) {}
      finally {
        latch.countDown();
      }
    };

    executor.submit(task);
    executor.submit(task);
    latch.await();

    // Ensure that balance didn't increase twice
    assertTrue(wallet.getBalance().compareTo(BigDecimal.valueOf(100)) <= 0);
  }

  @Test
  @Transactional
  public void testOptimisticLockingScenario() throws InterruptedException, ExecutionException {
    Wallet wallet = new Wallet();
    wallet.setId(1L);
    wallet.setUserId(1L);
    wallet.setBalance(BigDecimal.valueOf(100.00));
    wallet.setVersion(1L);

    TopUpRequest topUpRequest = new TopUpRequest(
        BigDecimal.valueOf(100),
        "4111111111111111",
        "concurrent-key"
    );

    when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
    when(transactionRepository.save(any())).thenReturn(new Transaction());
    when(stripeService.charge(anyString(), any())).thenReturn(new Payment("stripe-concurrent"));

    // This specific mock will interfere with others unless reset
    when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
      Wallet w = invocation.getArgument(0);
      if (w.getVersion() == 2L) {
        throw new OptimisticLockException("Version conflict");
      }
      return w;
    });

    Callable<Void> firstTransaction = () -> {
      walletService.topUpWallet(1L, topUpRequest);
      return null;
    };

    Callable<Void> secondTransaction = () -> {
      wallet.setVersion(2L);
      try {
        walletService.topUpWallet(1L, topUpRequest);
      } catch (OptimisticLockException e) {
        log.info("Expected OptimisticLockException: " + e.getMessage());
      }
      return null;
    };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Callable<Void>> tasks = List.of(firstTransaction, secondTransaction);
    List<Future<Void>> results = executor.invokeAll(tasks);

    for (Future<Void> result : results) {
      Exception exception = assertThrows(ExecutionException.class, () -> {
        // Code that should throw the exception
        result.get();
      });
      assertTrue(exception.getMessage().contains("Concurrency conflict: Wallet balance update failed due to concurrent modification."));
    }

    verify(transactionRepository, times(4)).save(any(Transaction.class));

    // ✅ Clean up or reset mocks if needed here
    reset(walletRepository);
  }
}

