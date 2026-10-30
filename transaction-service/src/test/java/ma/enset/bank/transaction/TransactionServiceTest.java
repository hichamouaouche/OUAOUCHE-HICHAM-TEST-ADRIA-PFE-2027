package ma.enset.bank.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountClient.AccountView;
import ma.enset.bank.transaction.client.AccountClient.BalanceRequest;
import ma.enset.bank.transaction.messaging.TransactionCreatedEvent;
import ma.enset.bank.transaction.messaging.TransactionEventPublisher;
import ma.enset.bank.transaction.repository.TransactionRepository;
import ma.enset.bank.transaction.service.AccountNotFoundException;
import ma.enset.bank.transaction.service.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.TransactionRuleException;
import ma.enset.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class TransactionServiceTest {

    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Autowired
    TransactionService service;
    @Autowired
    TransactionRepository repository;
    @Autowired
    CircuitBreakerRegistry breakers;
    @MockitoBean
    AccountClient client;
    @MockitoBean
    KafkaTemplate<String, Object> kafka;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        reset(client, kafka);
        breakers.circuitBreaker("account").reset();
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(new java.util.concurrent.CompletableFuture<>());
        when(client.getAccount(SOURCE)).thenReturn(view(SOURCE, "ACTIVE", "EUR", 500));
        when(client.getAccount(TARGET)).thenReturn(view(TARGET, "ACTIVE", "EUR", 0));
    }

    private static AccountView view(UUID id, String status, String currency, double balance) {
        return new AccountView(id, "C", status, currency, balance);
    }

    private static FeignException serverError() {
        Request request = Request.create(Request.HttpMethod.GET, "/", Map.of(), new byte[0], StandardCharsets.UTF_8,
                null);
        return new FeignException.InternalServerError("boom", request, null, Map.of());
    }

    private void createFromSource(String amount, String currency) {
        service.create(SOURCE, new BigDecimal(amount), currency, "GROCERY", "FR");
    }

    @Test
    void validTransactionDebitsAccountAndPublishesEventKeyedByAccount() {
        createFromSource("250.00", "EUR");

        verify(client).debit(eq(SOURCE), any(BalanceRequest.class));
        assertThat(repository.count()).isEqualTo(1);
        verify(kafka).send(eq(TransactionEventPublisher.TOPIC), eq(SOURCE.toString()),
                any(TransactionCreatedEvent.class));
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> createFromSource("0", "EUR")).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void amountAboveConfiguredCeilingIsRefused() {
        assertThatThrownBy(() -> createFromSource("1000.01", "EUR")).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void amountEqualToCeilingIsAccepted() {
        when(client.getAccount(SOURCE)).thenReturn(view(SOURCE, "ACTIVE", "EUR", 5000));
        createFromSource("1000", "EUR");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void blockedAccountIsRefused() {
        when(client.getAccount(SOURCE)).thenReturn(view(SOURCE, "BLOCKED", "EUR", 500));
        assertThatThrownBy(() -> createFromSource("10", "EUR")).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void currencyMismatchIsRefused() {
        assertThatThrownBy(() -> createFromSource("10", "USD")).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void insufficientBalanceIsRefused() {
        assertThatThrownBy(() -> createFromSource("500.01", "EUR")).isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void unknownAccountIsNotRetried() {
        when(client.getAccount(SOURCE)).thenThrow(new AccountNotFoundException(SOURCE));
        assertThatThrownBy(() -> createFromSource("10", "EUR")).isInstanceOf(AccountNotFoundException.class);
        verify(client, times(1)).getAccount(SOURCE);
    }

    @Test
    void failedDebitRollsBackAndPublishesNothing() {
        when(client.debit(eq(SOURCE), any())).thenThrow(new TransactionRuleException("refus"));

        assertThatThrownBy(() -> createFromSource("10", "EUR")).isInstanceOf(TransactionRuleException.class);

        assertThat(repository.count()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void outageIsRetriedThenReportedAsUnavailable() {
        when(client.getAccount(SOURCE)).thenThrow(serverError());

        assertThatThrownBy(() -> createFromSource("10", "EUR"))
                .isInstanceOf(AccountServiceUnavailableException.class);

        verify(client, times(3)).getAccount(SOURCE);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCalling() {
        when(client.getAccount(SOURCE)).thenThrow(serverError());
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> createFromSource("10", "EUR"))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }
        // 5 appels suffisent à ouvrir le circuit : les tentatives suivantes ne partent plus
        verify(client, times(5)).getAccount(SOURCE);
    }

    @Test
    void transferDebitsThenCreditsAndPublishesTwoEvents() {
        service.transfer(SOURCE, TARGET, new BigDecimal("100"), "EUR", "FR");

        verify(client).debit(eq(SOURCE), any());
        verify(client).credit(eq(TARGET), any());
        assertThat(repository.count()).isEqualTo(2);
        verify(kafka, times(2)).send(anyString(), anyString(), any());
    }

    @Test
    void transferToSameAccountIsRefused() {
        assertThatThrownBy(() -> service.transfer(SOURCE, SOURCE, BigDecimal.TEN, "EUR", "FR"))
                .isInstanceOf(TransactionRuleException.class);
    }

    @Test
    void failedCreditRefundsSourceAndRollsBack() {
        when(client.credit(eq(TARGET), any())).thenThrow(new TransactionRuleException("compte bloqué"));

        assertThatThrownBy(() -> service.transfer(SOURCE, TARGET, BigDecimal.TEN, "EUR", "FR"))
                .isInstanceOf(TransactionRuleException.class);

        verify(client).compensate(eq(SOURCE), any(BalanceRequest.class));
        assertThat(repository.findAll()).isEqualTo(List.of());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }
}
