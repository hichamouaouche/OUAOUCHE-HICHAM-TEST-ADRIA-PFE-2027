package ma.enset.bank.transaction.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration
public class TransactionEventPublisher {

    public static final String TOPIC = "bank.transactions.created";
    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public TransactionEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @Bean
    NewTopic transactionsCreatedTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    /** AFTER_COMMIT : aucun événement n'est émis si la persistance ou le débit échoue. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionCreatedEvent event) {
        // accountId comme clé : ordre garanti pour un même compte
        kafka.send(TOPIC, event.accountId().toString(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Publication Kafka échouée pour la transaction {}", event.transactionId(), error);
            }
        });
    }
}
