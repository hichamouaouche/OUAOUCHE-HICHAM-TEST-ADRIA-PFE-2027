package ma.enset.bank.account.mcp;

import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.service.AccountService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Outils en lecture seule : l'agent n'a jamais plus de droits qu'un utilisateur standard. */
@Component
public class AccountMcpTools {

    private final AccountService service;

    public AccountMcpTools(AccountService service) {
        this.service = service;
    }

    @McpTool(name = "list_accounts", description = "Liste tous les comptes bancaires")
    public List<Account> listAccounts() {
        return service.findAll();
    }

    @McpTool(name = "get_account", description = "Retourne un compte (statut, devise, solde) à partir de son UUID")
    public Account getAccount(@McpToolParam(description = "UUID du compte") String accountId) {
        return service.findById(UUID.fromString(accountId));
    }
}
