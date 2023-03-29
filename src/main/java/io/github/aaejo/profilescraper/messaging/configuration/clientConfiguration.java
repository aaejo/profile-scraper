package io.github.aaejo.profilescraper.messaging.configuration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import io.github.aaejo.finder.client.FinderClient;

@Configuration
public class clientConfiguration {
    @Bean
    public FinderClient client() {
        // Any client settings that should apply to all Jsoup connections
        // can be applied here
        Connection session = Jsoup
                .newSession()
                .ignoreHttpErrors(true); // We want to be able to inspect HTTP errors ourselves

        RetryTemplate retryTemplate = RetryTemplate.builder()
                                        .maxAttempts(3) // Initial + 2 retries
                                        .fixedBackoff(2000L) // Wait 2 seconds before retrying
                                        .build();

        return new FinderClient(session, retryTemplate);
    }
}
