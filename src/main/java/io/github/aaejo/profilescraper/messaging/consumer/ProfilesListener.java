package io.github.aaejo.profilescraper.messaging.consumer;

import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Profile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@KafkaListener(id = "profile-scraper", topics = "profiles")
public class ProfilesListener {

    @KafkaHandler
    public void handle(Profile profile) {
        log.info(profile.toString());
    }
}
