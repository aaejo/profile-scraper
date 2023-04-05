package io.github.aaejo.profilescraper.messaging.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * @author Omri Harary
 */
@Configuration
public class TopicConfiguration {

    @Bean
    public NewTopic profilesTopic() {
        return TopicBuilder
                .name("profiles")
                .build();
    }

    /**
     * Dead-letter topic for profiles that failed to process
     * This topic is not subscribed to anywhere, it is just for later review
     */
    @Bean
    public NewTopic profilesDLT() {
        return TopicBuilder
                .name("profiles.DLT")
                .build();
    }

    @Bean
    public NewTopic manualInterventionTopic() {
        return TopicBuilder
                .name("manual-intervention")
                .build();
    }

    @Bean
    public NewTopic reviewersDataTopic() {
        return TopicBuilder
                .name("reviewers-data")
                .build();
    }
}
