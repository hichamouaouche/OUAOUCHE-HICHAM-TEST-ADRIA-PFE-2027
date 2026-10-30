package ma.enset.bank.account.service;

/** Violation d'une règle métier : compte bloqué, solde insuffisant, montant invalide. */
public class AccountRuleException extends RuntimeException {

    public AccountRuleException(String message) {
        super(message);
    }
}
