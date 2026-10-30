package ma.enset.bank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Modèle minimal du sujet, complété par type et transferId pour représenter les virements. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;
    private UUID accountId;
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    private String currency;
    private String merchantCategory;
    private String country;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    private UUID transferId;

    protected Transaction() {
    }

    public Transaction(UUID accountId, BigDecimal amount, String currency, String merchantCategory, String country,
                       TransactionType type, UUID transferId) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.createdAt = Instant.now();
        this.type = type;
        this.transferId = transferId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public String getCountry() {
        return country;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TransactionType getType() {
        return type;
    }

    public UUID getTransferId() {
        return transferId;
    }
}
