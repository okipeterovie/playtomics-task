# 🏦 Playtomic Wallet Service

This service allows users to manage their wallet and perform top-ups using Stripe. It includes two main endpoints and supports optimistic locking to handle concurrent access.

## 🚀 Endpoints

### 1. Get Wallet
**GET /wallets/{id}**
- Retrieves the wallet for the specified user ID.

### 2. Top-Up Wallet
**POST /wallets/{id}/top-up**
- Tops up the user's wallet using a Stripe card.
- Requires amount, card number, and idempotency key.
- Handles duplicate requests with the same idempotency key.
- Uses optimistic locking to avoid concurrent balance updates.

## 🧪 Testing

- Unit tests are written using JUnit 5 and Mockito.
- Includes concurrency tests to simulate optimistic locking scenarios.
- To run all tests, "mvn test"

## ⚙️ Technologies

- Java 17
- Spring Boot
- JUnit & Mockito
- Stripe (mocked)
- Jakarta Persistence (Optimistic Locking with `@Version`)
