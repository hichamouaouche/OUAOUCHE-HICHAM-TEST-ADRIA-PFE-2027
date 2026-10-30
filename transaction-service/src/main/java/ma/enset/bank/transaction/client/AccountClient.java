package ma.enset.bank.transaction.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-service", configuration = AccountClientConfig.class)
public interface AccountClient {

    @GetMapping("/api/accounts/{id}")
    AccountView getAccount(@PathVariable UUID id);

    @PostMapping("/internal/accounts/{id}/debit")
    AccountView debit(@PathVariable UUID id, @RequestBody BalanceRequest request);

    @PostMapping("/internal/accounts/{id}/credit")
    AccountView credit(@PathVariable UUID id, @RequestBody BalanceRequest request);

    @PostMapping("/internal/accounts/{id}/compensate")
    AccountView compensate(@PathVariable UUID id, @RequestBody BalanceRequest request);

    record AccountView(UUID id, String customerReference, String status, String currency, double balance) {
    }

    record BalanceRequest(UUID operationId, double amount) {
    }
}
