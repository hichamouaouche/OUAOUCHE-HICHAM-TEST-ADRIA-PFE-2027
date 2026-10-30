# Partie B : conception et architecture

## Schéma d'architecture

```mermaid
flowchart LR
    web["bank-web (Angular)"] --> gw["gateway-service :8080"]
    rest["Client REST"] --> gw
    tg["Telegram"] <--> ast

    gw --> acc["account-service :8081"]
    gw --> trx["transaction-service :8082"]
    gw --> ast["assistant-service :8083"]

    trx -- "OpenFeign + Resilience4J" --> acc
    ast -- "MCP" --> acc
    ast -- "MCP" --> trx
    ast -- "API compatible OpenAI" --> llm["LLM (Ollama Cloud)"]

    trx -- "bank.transactions.created" --> kafka[("Kafka")]
    kafka --> cons["transaction-consumer-service :8084"]

    acc --> dba[("PostgreSQL : accounts")]
    trx --> dbt[("PostgreSQL : transactions")]

    kc["Keycloak :8180"] -. "JWT" .-> gw
    kc -. "JWT" .-> acc
    kc -. "JWT" .-> trx
    kc -. "JWT" .-> ast
    web -. "connexion PKCE" .-> kc

    cfg["config-service :8888"] -. "configuration" .-> acc
    cfg -. "bank.transaction.max-amount" .-> trx
    eur["discovery-service :8761"] -. "découverte" .-> gw
    eur -. "découverte" .-> trx
```

## Diagramme de classes

```mermaid
classDiagram
    class Account {
        UUID id
        String customerReference
        AccountStatus status
        String currency
        double balance
    }
    class AccountStatus {
        <<enumeration>>
        ACTIVE
        BLOCKED
    }
    class BalanceOperation {
        UUID operationId
        UUID accountId
        double delta
    }
    class AccountService {
        findAll() List~Account~
        findById(id) Account
        create(reference, currency, balance) Account
        changeStatus(id, status) Account
        debit(id, operationId, amount) Account
        credit(id, operationId, amount) Account
        compensate(id, operationId, amount) Account
    }
    class Transaction {
        UUID id
        UUID accountId
        BigDecimal amount
        String currency
        String merchantCategory
        String country
        Instant createdAt
        TransactionType type
        UUID transferId
    }
    class TransactionType {
        <<enumeration>>
        DEBIT
        CREDIT
    }
    class TransactionService {
        find(accountId) List~Transaction~
        findById(id) Transaction
        create(accountId, amount, currency, category, country) Transaction
        transfer(source, target, amount, currency, country) List~Transaction~
    }
    class TransactionCreatedEvent {
        UUID eventId
        String eventType
        UUID transactionId
        UUID accountId
        BigDecimal amount
        String currency
        Instant occurredAt
    }
    class AccountGateway {
        getAccount(id) AccountView
        debit(id, operationId, amount) AccountView
        credit(id, operationId, amount) AccountView
        compensate(id, operationId, amount) AccountView
    }

    Account --> AccountStatus
    AccountService --> Account
    AccountService --> BalanceOperation
    Transaction --> TransactionType
    TransactionService --> Transaction
    TransactionService --> AccountGateway
    TransactionService ..> TransactionCreatedEvent : publie
    AccountGateway ..> AccountService : Feign / HTTP
    Transaction ..> Account : accountId (référence par identifiant)
```

`AccountController`, `AccountGraphqlController` et `AccountMcpTools` appellent tous `AccountService`.
`TransactionController`, `TransactionGraphqlController` et `TransactionMcpTools` appellent tous `TransactionService`.

## Flux d'une nouvelle transaction

```mermaid
sequenceDiagram
    participant C as Client
    participant T as transaction-service
    participant A as account-service
    participant K as Kafka
    C->>T: POST /api/transactions
    T->>T: montant > 0 et <= plafond
    T->>A: GET /api/accounts/{id} (Retry, CircuitBreaker)
    A-->>T: statut, devise, solde
    T->>T: compte actif, devise identique, solde suffisant
    T->>T: INSERT transaction (non validée)
    T->>A: POST /internal/accounts/{id}/debit (idempotent)
    alt débit accepté
        A-->>T: nouveau solde
        T->>T: COMMIT
        T->>K: TransactionCreated (clé = accountId)
        T-->>C: 201
    else refus ou panne
        T->>T: ROLLBACK
        T-->>C: 422 ou 503
    end
```
