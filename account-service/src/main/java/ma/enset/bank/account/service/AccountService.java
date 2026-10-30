package ma.enset.bank.account.service;

import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.domain.BalanceOperation;
import ma.enset.bank.account.repository.AccountRepository;
import ma.enset.bank.account.repository.BalanceOperationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Couche unique appelée par les connecteurs REST, GraphQL et MCP. */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final BalanceOperationRepository operations;

    public AccountService(AccountRepository accounts, BalanceOperationRepository operations) {
        this.accounts = accounts;
        this.operations = operations;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accounts.findAll();
    }

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Account create(String customerReference, String currency, double initialBalance) {
        if (customerReference == null || customerReference.isBlank()) {
            throw new AccountRuleException("La référence client est obligatoire");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new AccountRuleException("La devise doit être un code ISO à 3 lettres majuscules");
        }
        if (initialBalance < 0) {
            throw new AccountRuleException("Le solde initial ne peut pas être négatif");
        }
        return accounts.save(new Account(customerReference, currency, initialBalance));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Account changeStatus(UUID id, AccountStatus status) {
        Account account = findById(id);
        account.setStatus(status);
        return account;
    }

    @PreAuthorize("hasRole('INTERNAL')")
    @Transactional
    public Account debit(UUID id, UUID operationId, double amount) {
        return apply(id, operationId, -positive(amount), true);
    }

    @PreAuthorize("hasRole('INTERNAL')")
    @Transactional
    public Account credit(UUID id, UUID operationId, double amount) {
        return apply(id, operationId, positive(amount), false);
    }

    /** Annule un débit : contrairement à credit, reste possible si le compte vient d'être bloqué. */
    @PreAuthorize("hasRole('INTERNAL')")
    @Transactional
    public Account compensate(UUID id, UUID operationId, double amount) {
        Account account = accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (!operations.existsById(operationId)) {
            account.setBalance(account.getBalance() + positive(amount));
            operations.save(new BalanceOperation(operationId, id, amount));
        }
        return account;
    }

    private Account apply(UUID id, UUID operationId, double delta, boolean mustCover) {
        // Le verrou sérialise les mouvements concurrents et le contrôle de rejeu sur un même compte
        Account account = accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (operations.existsById(operationId)) {
            return account;
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountRuleException("Le compte " + id + " n'est pas actif");
        }
        if (mustCover && account.getBalance() + delta < 0) {
            throw new AccountRuleException("Solde insuffisant sur le compte " + id);
        }
        account.setBalance(account.getBalance() + delta);
        operations.save(new BalanceOperation(operationId, id, delta));
        return account;
    }

    private static double positive(double amount) {
        if (amount <= 0) {
            throw new AccountRuleException("Le montant doit être strictement positif");
        }
        return amount;
    }
}
