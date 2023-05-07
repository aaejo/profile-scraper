package io.github.aaejo.profilescraper.messaging.producer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.IncompleteScrape;

/**
 * @author Omri Harary
 */
@Component
public class ManualInterventionProducer {
    private static final Logger log = LoggerFactory.getLogger(ManualInterventionProducer.class);

    private static final String TOPIC = "manual-intervention";

    private final KafkaTemplate<String, IncompleteScrape> template;

    public ManualInterventionProducer(KafkaTemplate<String, IncompleteScrape> template) {
        this.template = template;
    }

    public void send(final IncompleteScrape incompleteScrape) {
        CompletableFuture<SendResult<String, IncompleteScrape>> sendResultFuture = this.template.send(TOPIC, incompleteScrape);
        sendResultFuture.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Sent: " + incompleteScrape.toString());
            }
            else {
                log.error("Failed to send: " + incompleteScrape.toString(), ex);
            }
        });
    }
}
