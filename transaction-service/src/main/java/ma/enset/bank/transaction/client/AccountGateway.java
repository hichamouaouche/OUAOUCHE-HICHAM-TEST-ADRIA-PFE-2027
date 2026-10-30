package ma.enset.bank.transaction.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient.AccountView;
import ma.enset.bank.transaction.client.AccountClient.BalanceRequest;
import ma.enset.bank.transaction.service.AccountServiceUnavailableException;
import org.springframework.stereotype.Component;

/**
 * Applique Retry et CircuitBreaker aux appels Feign. Rejouer un mouvement est sûr
 * car account-service dédoublonne sur operationId. Les refus métier ne correspondent à
 * aucun fallback et remontent donc tels quels.
 */
@Component
public class AccountGateway {

    private static final String ACCOUNT = "account";

    private final AccountClient client;

    public AccountGateway(AccountClient client) {
        this.client = client;
    }

    @Retry(name = ACCOUNT, fallbackMethod = "unavailable")
    @CircuitBreaker(name = ACCOUNT)
    public AccountView getAccount(UUID id) {
        return client.getAccount(id);
    }

    @Retry(name = ACCOUNT, fallbackMethod = "unavailable")
    @CircuitBreaker(name = ACCOUNT)
    public AccountView debit(UUID id, UUID operationId, double amount) {
        return client.debit(id, new BalanceRequest(operationId, amount));
    }

    @Retry(name = ACCOUNT, fallbackMethod = "unavailable")
    @CircuitBreaker(name = ACCOUNT)
    public AccountView credit(UUID id, UUID operationId, double amount) {
        return client.credit(id, new BalanceRequest(operationId, amount));
    }

    @Retry(name = ACCOUNT, fallbackMethod = "unavailable")
    @CircuitBreaker(name = ACCOUNT)
    public AccountView compensate(UUID id, UUID operationId, double amount) {
        return client.compensate(id, new BalanceRequest(operationId, amount));
    }

    private AccountView unavailable(UUID id, FeignException cause) {
        throw new AccountServiceUnavailableException(cause);
    }

    private AccountView unavailable(UUID id, CallNotPermittedException cause) {
        throw new AccountServiceUnavailableException(cause);
    }

    private AccountView unavailable(UUID id, UUID operationId, double amount, FeignException cause) {
        throw new AccountServiceUnavailableException(cause);
    }

    private AccountView unavailable(UUID id, UUID operationId, double amount, CallNotPermittedException cause) {
        throw new AccountServiceUnavailableException(cause);
    }
}
