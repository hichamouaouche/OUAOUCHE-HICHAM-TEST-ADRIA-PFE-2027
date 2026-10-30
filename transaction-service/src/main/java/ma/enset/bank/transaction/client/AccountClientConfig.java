package ma.enset.bank.transaction.client;

import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ma.enset.bank.transaction.service.AccountNotFoundException;
import ma.enset.bank.transaction.service.TransactionRuleException;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Non annotée @Configuration : ne s'applique qu'à AccountClient. */
public class AccountClientConfig {

    /** Identité propre du service (client_credentials), car elle seule porte le rôle INTERNAL. */
    @Bean
    RequestInterceptor serviceTokenInterceptor(ClientRegistrationRepository registrations,
                                               OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        var request = OAuth2AuthorizeRequest.withClientRegistrationId("bank-internal")
                .principal("transaction-service").build();
        return template -> {
            OAuth2AuthorizedClient client = manager.authorize(request);
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }

    /** Traduit les refus métier pour qu'ils ne comptent ni comme pannes ni comme tentatives à rejouer. */
    @Bean
    ErrorDecoder accountErrorDecoder() {
        ErrorDecoder fallback = new ErrorDecoder.Default();
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new AccountNotFoundException(accountId(response));
            case 422 -> new TransactionRuleException(detail(response));
            default -> fallback.decode(methodKey, response);
        };
    }

    private static UUID accountId(Response response) {
        String[] segments = response.request().url().split("/");
        for (String segment : segments) {
            String clean = segment.split("\\?")[0];
            if (clean.length() == 36) {
                return UUID.fromString(clean);
            }
        }
        return null;
    }

    private static String detail(Response response) {
        if (response.body() == null) {
            return "Opération refusée par account-service";
        }
        try (var body = response.body().asInputStream()) {
            String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            return new tools.jackson.databind.ObjectMapper().readTree(json).path("detail").asString();
        } catch (IOException e) {
            return "Opération refusée par account-service";
        }
    }
}
