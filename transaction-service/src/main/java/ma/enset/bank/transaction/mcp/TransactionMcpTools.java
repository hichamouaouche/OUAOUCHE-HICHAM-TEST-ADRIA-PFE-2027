package ma.enset.bank.transaction.mcp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.service.TransactionService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TransactionMcpTools {

    private final TransactionService service;

    public TransactionMcpTools(TransactionService service) {
        this.service = service;
    }

    @McpTool(name = "list_transactions", description = "Liste les transactions d'un compte, les plus récentes d'abord")
    public List<Transaction> listTransactions(@McpToolParam(description = "UUID du compte") String accountId) {
        return service.find(UUID.fromString(accountId));
    }

    @McpTool(name = "create_transaction", description = "Enregistre un paiement qui débite un compte")
    public Transaction createTransaction(
            @McpToolParam(description = "UUID du compte") String accountId,
            @McpToolParam(description = "Montant strictement positif") BigDecimal amount,
            @McpToolParam(description = "Devise ISO, identique à celle du compte") String currency,
            @McpToolParam(description = "Catégorie du marchand, par exemple GROCERY") String merchantCategory,
            @McpToolParam(description = "Code pays, par exemple FR") String country) {
        return service.create(UUID.fromString(accountId), amount, currency, merchantCategory, country);
    }

    @McpTool(name = "transfer", description = "Effectue un virement d'un compte vers un autre")
    public List<Transaction> transfer(
            @McpToolParam(description = "UUID du compte à débiter") String sourceAccountId,
            @McpToolParam(description = "UUID du compte à créditer") String targetAccountId,
            @McpToolParam(description = "Montant strictement positif") BigDecimal amount,
            @McpToolParam(description = "Devise ISO commune aux deux comptes") String currency,
            @McpToolParam(description = "Code pays, par exemple FR") String country) {
        return service.transfer(UUID.fromString(sourceAccountId), UUID.fromString(targetAccountId), amount, currency,
                country);
    }
}
