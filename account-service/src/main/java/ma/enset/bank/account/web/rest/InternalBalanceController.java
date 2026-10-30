package ma.enset.bank.account.web.rest;

import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.service.AccountService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mouvements de solde réservés à transaction-service ; ce chemin n'est pas routé par la gateway. */
@RestController
@RequestMapping("/internal/accounts/{id}")
public class InternalBalanceController {

    private final AccountService service;

    public InternalBalanceController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/debit")
    public Account debit(@PathVariable UUID id, @RequestBody BalanceRequest request) {
        return service.debit(id, request.operationId(), request.amount());
    }

    @PostMapping("/credit")
    public Account credit(@PathVariable UUID id, @RequestBody BalanceRequest request) {
        return service.credit(id, request.operationId(), request.amount());
    }

    @PostMapping("/compensate")
    public Account compensate(@PathVariable UUID id, @RequestBody BalanceRequest request) {
        return service.compensate(id, request.operationId(), request.amount());
    }

    public record BalanceRequest(UUID operationId, double amount) {
    }
}
