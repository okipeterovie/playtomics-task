package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
