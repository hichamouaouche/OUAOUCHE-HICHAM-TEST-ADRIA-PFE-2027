package ma.enset.bank.consumer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(UUID eventId, String eventType, UUID transactionId, UUID accountId,
                                      BigDecimal amount, String currency, Instant occurredAt) {
}
