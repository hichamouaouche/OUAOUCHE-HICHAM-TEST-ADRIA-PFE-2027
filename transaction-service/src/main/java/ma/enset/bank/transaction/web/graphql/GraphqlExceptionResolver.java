package ma.enset.bank.transaction.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import ma.enset.bank.transaction.service.AccountNotFoundException;
import ma.enset.bank.transaction.service.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.TransactionNotFoundException;
import ma.enset.bank.transaction.service.TransactionRuleException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type;
        if (ex instanceof AccountNotFoundException || ex instanceof TransactionNotFoundException) {
            type = ErrorType.NOT_FOUND;
        } else if (ex instanceof TransactionRuleException) {
            type = ErrorType.BAD_REQUEST;
        } else if (ex instanceof AccountServiceUnavailableException) {
            type = ErrorType.INTERNAL_ERROR;
        } else {
            return null;
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(ex.getMessage()).build();
    }
}
