package ma.enset.bank.transaction.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping("/api/transactions")
    public List<Transaction> find(@RequestParam(required = false) UUID accountId) {
        return service.find(accountId);
    }

    @GetMapping("/api/transactions/{id}")
    public Transaction findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/api/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@Valid @RequestBody CreateTransactionRequest request) {
        return service.create(request.accountId(), request.amount(), request.currency(), request.merchantCategory(),
                request.country());
    }

    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Transaction> transfer(@Valid @RequestBody TransferRequest request) {
        return service.transfer(request.sourceAccountId(), request.targetAccountId(), request.amount(),
                request.currency(), request.country());
    }

    public record CreateTransactionRequest(@NotNull UUID accountId, @NotNull BigDecimal amount,
                                           @NotBlank String currency, @NotBlank String merchantCategory,
                                           @NotBlank String country) {
    }

    public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID targetAccountId,
                                  @NotNull BigDecimal amount, @NotBlank String currency, @NotBlank String country) {
    }
}
