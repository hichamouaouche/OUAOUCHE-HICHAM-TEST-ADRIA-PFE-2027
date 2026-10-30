package ma.enset.bank.transaction.web.rest;

import ma.enset.bank.transaction.service.AccountNotFoundException;
import ma.enset.bank.transaction.service.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.TransactionNotFoundException;
import ma.enset.bank.transaction.service.TransactionRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, TransactionNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TransactionRuleException.class)
    ProblemDetail rule(TransactionRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    ProblemDetail unavailable(AccountServiceUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
