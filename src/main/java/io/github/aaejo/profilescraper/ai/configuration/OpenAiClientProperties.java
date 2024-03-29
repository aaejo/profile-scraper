package io.github.aaejo.profilescraper.ai.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Omri Harary
 */
@ConfigurationProperties("aaejo.jds.openai.client")
public record OpenAiClientProperties(String promptInstructions, String apiUrl, String apiKey, String model) {
}
