package ma.enset.bank.account.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import ma.enset.bank.account.service.AccountNotFoundException;
import ma.enset.bank.account.service.AccountRuleException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type;
        if (ex instanceof AccountNotFoundException) {
            type = ErrorType.NOT_FOUND;
        } else if (ex instanceof AccountRuleException) {
            type = ErrorType.BAD_REQUEST;
        } else {
            return null;
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(ex.getMessage()).build();
    }
}
