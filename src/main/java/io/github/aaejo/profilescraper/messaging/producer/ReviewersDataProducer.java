package io.github.aaejo.profilescraper.messaging.producer;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Reviewer;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
@Component
public class ReviewersDataProducer {

    private static final String TOPIC = "reviewers-data";

    private final KafkaTemplate<String, Reviewer> template;

    public ReviewersDataProducer(KafkaTemplate<String, Reviewer> template) {
        this.template = template;
    }

    public void send(final Reviewer reviewer) {
        CompletableFuture<SendResult<String, Reviewer>> sendResultFuture = this.template.send(TOPIC, reviewer);
        sendResultFuture.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Sent: " + reviewer.toString());
            }
            else {
                log.error("Failed to send: " + reviewer.toString(), ex);
            }
        });
    }
}
