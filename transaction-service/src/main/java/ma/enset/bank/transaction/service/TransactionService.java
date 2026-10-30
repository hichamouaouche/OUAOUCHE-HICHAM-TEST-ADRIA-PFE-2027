package ma.enset.bank.transaction.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient.AccountView;
import ma.enset.bank.transaction.client.AccountGateway;
import ma.enset.bank.transaction.domain.Transaction;
import ma.enset.bank.transaction.domain.TransactionType;
import ma.enset.bank.transaction.messaging.TransactionCreatedEvent;
import ma.enset.bank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Couche unique appelée par les connecteurs REST, GraphQL et MCP. */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final String TRANSFER_CATEGORY = "TRANSFER";

    private final TransactionRepository transactions;
    private final AccountGateway accounts;
    private final BankProperties properties;
    private final ApplicationEventPublisher events;

    public TransactionService(TransactionRepository transactions, AccountGateway accounts, BankProperties properties,
                              ApplicationEventPublisher events) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.properties = properties;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Transaction> find(UUID accountId) {
        return accountId == null
                ? transactions.findAllByOrderByCreatedAtDesc()
                : transactions.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    /** Une exception pendant la mise à jour du solde annule l'insertion locale. */
    @Transactional
    public Transaction create(UUID accountId, BigDecimal amount, String currency, String merchantCategory,
                              String country) {
        checkAmount(amount);
        AccountView account = accounts.getAccount(accountId);
        checkDebitable(account, amount, currency);

        Transaction saved = transactions.saveAndFlush(new Transaction(accountId, amount, currency, merchantCategory,
                country, TransactionType.DEBIT, null));
        accounts.debit(accountId, saved.getId(), amount.doubleValue());

        events.publishEvent(TransactionCreatedEvent.of(saved));
        return saved;
    }

    @Transactional
    public List<Transaction> transfer(UUID sourceId, UUID targetId, BigDecimal amount, String currency,
                                      String country) {
        checkAmount(amount);
        if (sourceId.equals(targetId)) {
            throw new TransactionRuleException("Les comptes source et destination doivent être différents");
        }
        checkDebitable(accounts.getAccount(sourceId), amount, currency);
        AccountView target = accounts.getAccount(targetId);
        checkActive(target);
        checkCurrency(target, currency);

        UUID transferId = UUID.randomUUID();
        Transaction debit = transactions.saveAndFlush(new Transaction(sourceId, amount, currency, TRANSFER_CATEGORY,
                country, TransactionType.DEBIT, transferId));
        Transaction credit = transactions.saveAndFlush(new Transaction(targetId, amount, currency, TRANSFER_CATEGORY,
                country, TransactionType.CREDIT, transferId));

        accounts.debit(sourceId, debit.getId(), amount.doubleValue());
        try {
            accounts.credit(targetId, credit.getId(), amount.doubleValue());
        } catch (RuntimeException e) {
            refund(sourceId, transferId, amount);
            throw e;
        }

        events.publishEvent(TransactionCreatedEvent.of(debit));
        events.publishEvent(TransactionCreatedEvent.of(credit));
        return List.of(debit, credit);
    }

    private void refund(UUID sourceId, UUID transferId, BigDecimal amount) {
        try {
            accounts.compensate(sourceId, transferId, amount.doubleValue());
        } catch (RuntimeException e) {
            // Le solde source reste débité : à régulariser manuellement à partir de cette trace
            log.error("Compensation impossible : compte={} virement={} montant={}", sourceId, transferId, amount, e);
        }
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new TransactionRuleException("Le montant doit être strictement positif");
        }
        if (amount.compareTo(properties.getMaxAmount()) > 0) {
            throw new TransactionRuleException("Le montant dépasse le plafond de " + properties.getMaxAmount());
        }
    }

    private void checkDebitable(AccountView account, BigDecimal amount, String currency) {
        checkActive(account);
        checkCurrency(account, currency);
        if (BigDecimal.valueOf(account.balance()).compareTo(amount) < 0) {
            throw new TransactionRuleException("Solde insuffisant sur le compte " + account.id());
        }
    }

    private void checkActive(AccountView account) {
        if (!"ACTIVE".equals(account.status())) {
            throw new TransactionRuleException("Le compte " + account.id() + " n'est pas actif");
        }
    }

    private void checkCurrency(AccountView account, String currency) {
        if (!account.currency().equals(currency)) {
            throw new TransactionRuleException(
                    "La devise " + currency + " ne correspond pas à celle du compte (" + account.currency() + ")");
        }
    }
}
