package io.github.aaejo.profilescraper.ai.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiClientProperties.class)
public class OpenAiClientConfiguration {
    
}
