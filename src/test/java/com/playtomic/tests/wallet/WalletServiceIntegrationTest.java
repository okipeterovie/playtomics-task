package com.playtomic.tests.wallet;

import com.playtomic.tests.wallet.entity.Wallet;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import com.playtomic.tests.wallet.repository.WalletRepository;
import com.playtomic.tests.wallet.service.Payment;
import com.playtomic.tests.wallet.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class WalletServiceIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private WalletRepository walletRepository;
  @Autowired
  private TransactionRepository transactionRepository;

  @MockBean
  private StripeService stripeService;

  private Wallet wallet;

  @BeforeEach
  void setup() {
    transactionRepository.deleteAll();
    walletRepository.deleteAll();

    wallet = new Wallet();
    wallet.setUserId(1L);
    wallet.setBalance(BigDecimal.ZERO);
    wallet = walletRepository.save(wallet);
  }

  @Test
  void testSuccessfulTopUp() throws Exception {
    when(stripeService.charge(anyString(), any())).thenReturn(new Payment("STRIPE_REF_123"));

    String json = """
            {
                "amount": 50.00,
                "cardNumber": "4111111111111111",
                "idempotencyKey": "unique-key-123"
            }
        """;

    mockMvc.perform(post("/" + wallet.getId() + "/top-up")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
    assertEquals(new BigDecimal("50.00"), updated.getBalance());
  }

  @Test
  void testStripe422Rejected() throws Exception {
    when(stripeService.charge(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

    String json = """
            {
                "amount": 5.00,
                "cardNumber": "4000000000000002",
                "idempotencyKey": "unique-key-422"
            }
        """;

    mockMvc.perform(post("/" + wallet.getId() + "/top-up")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(containsString("Payment rejected")));
  }
}
