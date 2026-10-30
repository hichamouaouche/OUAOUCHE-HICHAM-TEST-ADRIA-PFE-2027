package ma.enset.bank.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Journalise uniquement : ce service n'accède à aucune donnée bancaire. */
@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    @KafkaListener(topics = "bank.transactions.created")
    public void onTransactionCreated(@Payload TransactionCreatedEvent event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        log.info("Événement reçu [clé={}, partition={}] : {}", key, partition, event);
    }
}
