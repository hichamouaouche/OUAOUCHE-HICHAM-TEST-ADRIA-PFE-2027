package ma.enset.bank.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
        // Préfixé par l'utilisateur : personne ne peut relire la conversation d'un autre
        String conversationId = jwt.getSubject() + ":" + request.conversationId();
        return new ChatResponse(assistant.ask(conversationId, request.message()));
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail assistantFailure(RuntimeException e) {
        log.error("Échec de l'assistant", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "L'assistant est momentanément indisponible");
    }

    public record ChatRequest(@NotBlank String conversationId, @NotBlank String message) {
    }

    public record ChatResponse(String reply) {
    }
}
