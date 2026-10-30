package ma.enset.bank.transaction.web.graphql;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.service.TransactionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TransactionGraphqlController {

    private final TransactionService service;

    public TransactionGraphqlController(TransactionService service) {
        this.service = service;
    }

    @QueryMapping
    public List<Transaction> transactions(@Argument UUID accountId) {
        return service.find(accountId);
    }

    @QueryMapping
    public Transaction transaction(@Argument UUID id) {
        return service.findById(id);
    }

    @MutationMapping
    public Transaction createTransaction(@Argument CreateTransactionInput input) {
        return service.create(input.accountId(), input.amount(), input.currency(), input.merchantCategory(),
                input.country());
    }

    @MutationMapping
    public List<Transaction> transfer(@Argument TransferInput input) {
        return service.transfer(input.sourceAccountId(), input.targetAccountId(), input.amount(), input.currency(),
                input.country());
    }

    public record CreateTransactionInput(UUID accountId, BigDecimal amount, String currency, String merchantCategory,
                                         String country) {
    }

    public record TransferInput(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency,
                                String country) {
    }
}
