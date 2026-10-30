package ma.enset.bank.transaction.service;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(Throwable cause) {
        super("Le service des comptes est momentanément indisponible, réessayez plus tard", cause);
    }
}
