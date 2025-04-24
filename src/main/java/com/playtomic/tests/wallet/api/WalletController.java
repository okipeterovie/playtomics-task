package com.playtomic.tests.wallet.api;

import com.playtomic.tests.wallet.dto.TopUpRequest;
import com.playtomic.tests.wallet.dto.TransactionDto;
import com.playtomic.tests.wallet.dto.WalletDto;
import com.playtomic.tests.wallet.entity.Transaction;
import com.playtomic.tests.wallet.entity.Wallet;
import com.playtomic.tests.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WalletController {
  private final WalletService walletService;
  private final Logger log = LoggerFactory.getLogger(WalletController.class);

  public WalletController(WalletService walletService) {
    this.walletService = walletService;
  }

  @RequestMapping("/")
  void log() {
    log.info("Logging from /");
  }

  @GetMapping("/{id}")
  public ResponseEntity<WalletDto> getWallet(@PathVariable Long id) {
    Wallet wallet = walletService.getWallet(id);
    return ResponseEntity.ok(WalletDto.from(wallet));
  }

  @PostMapping("/{id}/top-up")
  public ResponseEntity<TransactionDto> topUpWallet(
      @PathVariable Long id,
      @RequestBody TopUpRequest request) {
    Transaction transaction = walletService.topUpWallet(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(TransactionDto.from(transaction));
  }
}
