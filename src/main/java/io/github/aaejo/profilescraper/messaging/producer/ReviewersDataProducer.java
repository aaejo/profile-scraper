package io.github.aaejo.profilescraper.messaging.producer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Reviewer;

/**
 * @author Omri Harary
 */
@Component
public class ReviewersDataProducer {
    private static final Logger log = LoggerFactory.getLogger(ReviewersDataProducer.class);

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
