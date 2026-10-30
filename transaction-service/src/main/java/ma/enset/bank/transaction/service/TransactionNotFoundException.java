package ma.enset.bank.transaction.service;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID id) {
        super("Transaction introuvable : " + id);
    }
}
