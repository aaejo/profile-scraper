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
