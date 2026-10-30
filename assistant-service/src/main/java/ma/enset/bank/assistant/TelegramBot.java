package ma.enset.bank.assistant;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Client Telegram en long polling, actif seulement si un token est fourni.
 * Un bot est public : seuls les chats listés peuvent déclencher des opérations.
 */
@Component
@ConditionalOnExpression("!'${assistant.telegram.token:}'.isBlank()")
public class TelegramBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final AssistantService assistant;
    private final RestClient telegram;
    private final Set<Long> allowedChats;
    private volatile Thread poller;

    public TelegramBot(AssistantService assistant, RestClient.Builder builder,
                       @Value("${assistant.telegram.token}") String token,
                       @Value("${assistant.telegram.allowed-chat-ids:}") String allowedChatIds) {
        this.assistant = assistant;
        this.telegram = builder.baseUrl("https://api.telegram.org/bot" + token).build();
        this.allowedChats = Arrays.stream(allowedChatIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    @PostConstruct
    void start() {
        poller = Thread.ofVirtual().name("telegram-poller").start(this::poll);
    }

    @PreDestroy
    void stop() {
        poller.interrupt();
    }

    private void poll() {
        long offset = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                JsonNode updates = telegram.get()
                        .uri("/getUpdates?timeout=30&offset={offset}", offset)
                        .retrieve()
                        .body(JsonNode.class);
                for (JsonNode update : updates.path("result")) {
                    offset = update.path("update_id").asLong() + 1;
                    handle(update.path("message"));
                }
            } catch (RuntimeException e) {
                // Le token figure dans l'URL : on ne journalise jamais le détail de l'exception
                log.warn("Échec du polling Telegram ({}), nouvel essai dans 5 s", e.getClass().getSimpleName());
                if (!pause()) {
                    return;
                }
            }
        }
    }

    private void handle(JsonNode message) {
        String text = message.path("text").asString("");
        long chatId = message.path("chat").path("id").asLong();
        if (text.isBlank()) {
            return;
        }
        if (!allowedChats.contains(chatId)) {
            log.warn("Message Telegram refusé depuis le chat {}", chatId);
            send(chatId, "Ce chat n'est pas autorisé. Identifiant : " + chatId);
            return;
        }
        String reply;
        try {
            reply = assistant.ask("telegram:" + chatId, text);
        } catch (RuntimeException e) {
            log.error("Échec de l'assistant pour le chat {}", chatId, e);
            reply = "L'assistant est momentanément indisponible.";
        }
        send(chatId, reply);
    }

    private void send(long chatId, String text) {
        String bounded = text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
        telegram.post().uri("/sendMessage")
                .body(Map.of("chat_id", chatId, "text", bounded))
                .retrieve()
                .toBodilessEntity();
    }

    private boolean pause() {
        try {
            Thread.sleep(5000);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
