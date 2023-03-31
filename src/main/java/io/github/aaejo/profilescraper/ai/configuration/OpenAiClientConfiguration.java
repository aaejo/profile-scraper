package io.github.aaejo.profilescraper.ai.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Omri Harary
 */
@Configuration
@EnableConfigurationProperties(OpenAiClientProperties.class)
public class OpenAiClientConfiguration {
    
}
