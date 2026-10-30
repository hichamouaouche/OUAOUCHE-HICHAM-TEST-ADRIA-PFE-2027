package ma.enset.bank.account.web.graphql;

import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AccountGraphqlController {

    private final AccountService service;

    public AccountGraphqlController(AccountService service) {
        this.service = service;
    }

    @QueryMapping
    public List<Account> accounts() {
        return service.findAll();
    }

    @QueryMapping
    public Account account(@Argument UUID id) {
        return service.findById(id);
    }

    @MutationMapping
    public Account createAccount(@Argument CreateAccountInput input) {
        return service.create(input.customerReference(), input.currency(), input.initialBalance());
    }

    @MutationMapping
    public Account changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return service.changeStatus(id, status);
    }

    public record CreateAccountInput(String customerReference, String currency, double initialBalance) {
    }
}
