package com.playtomic.tests.wallet.exceptions;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNotFound(EntityNotFoundException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(PaymentRejectedException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public Map<String, String> handlePaymentRejected(PaymentRejectedException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleDuplicateRequest(IllegalStateException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleOtherErrors(Exception ex) {
    log.info("Ran into an unexpected error: {}", ex.getMessage());
    return Map.of("error", "Unexpected error: Please contact support");
  }
}
