package ma.enset.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Trace d'un mouvement déjà appliqué : rend le débit et le crédit idempotents,
 * donc sûrs à rejouer par le Retry de transaction-service.
 */
@Entity
@Table(name = "balance_operations")
public class BalanceOperation {

    @Id
    private UUID operationId;
    private UUID accountId;
    private double delta;

    protected BalanceOperation() {
    }

    public BalanceOperation(UUID operationId, UUID accountId, double delta) {
        this.operationId = operationId;
        this.accountId = accountId;
        this.delta = delta;
    }

    public UUID getOperationId() {
        return operationId;
    }
}
