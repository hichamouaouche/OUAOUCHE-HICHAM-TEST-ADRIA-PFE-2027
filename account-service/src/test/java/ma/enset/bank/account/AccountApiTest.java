package ma.enset.bank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.mcp.AccountMcpTools;
import ma.enset.bank.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AccountRepository accounts;
    @Autowired
    AccountMcpTools mcpTools;
    @MockitoBean
    JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Account account(double balance) {
        return accounts.save(new Account("CUST-1", "EUR", balance));
    }

    private String balanceBody(UUID operationId, double amount) {
        return "{\"operationId\":\"%s\",\"amount\":%s}".formatted(operationId, amount);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void standardUserCannotCreateAccount() throws Exception {
        mvc.perform(post("/api/accounts").with(role("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"C\",\"currency\":\"EUR\",\"initialBalance\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAccount() throws Exception {
        mvc.perform(post("/api/accounts").with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"C\",\"currency\":\"EUR\",\"initialBalance\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(10.0));
    }

    @Test
    void userCannotMoveBalanceDirectly() throws Exception {
        Account account = account(100);
        mvc.perform(post("/internal/accounts/{id}/credit", account.getId()).with(role("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(balanceBody(UUID.randomUUID(), 50)))
                .andExpect(status().isForbidden());
    }

    @Test
    void debitReplayIsAppliedOnlyOnce() throws Exception {
        Account account = account(100);
        String body = balanceBody(UUID.randomUUID(), 30);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/accounts/{id}/debit", account.getId()).with(role("INTERNAL"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualTo(70);
    }

    @Test
    void debitAboveBalanceIsRefused() throws Exception {
        Account account = account(10);
        mvc.perform(post("/internal/accounts/{id}/debit", account.getId()).with(role("INTERNAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(balanceBody(UUID.randomUUID(), 50)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void graphqlQueryUsesSameData() throws Exception {
        Account account = account(42);
        String query = "{\"query\":\"{ account(id: \\\"%s\\\") { balance status } }\"}".formatted(account.getId());
        mvc.perform(post("/graphql").with(role("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(42.0))
                .andExpect(jsonPath("$.data.account.status").value("ACTIVE"));
    }

    @Test
    void mcpToolUsesSameData() {
        Account account = account(7);
        assertThat(mcpTools.getAccount(account.getId().toString()).getBalance()).isEqualTo(7);
    }
}
