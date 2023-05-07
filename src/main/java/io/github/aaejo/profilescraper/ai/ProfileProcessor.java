package io.github.aaejo.profilescraper.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.profilescraper.ai.configuration.OpenAiClientProperties;
import io.github.aaejo.profilescraper.exception.BogusProfileException;

/**
 * @author Aidan Richards
 */
@Component
public class ProfileProcessor {
    private static final Logger log = LoggerFactory.getLogger(ProfileProcessor.class);

    private final RestTemplate restTemplate;
    private final OpenAiClientProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * @param properties
     */
    public ProfileProcessor(RestTemplateBuilder restTemplateBuilder, OpenAiClientProperties properties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param profileContents
     * @return
     */
    public ProfileInfo process(String profileContents) throws Exception { //give this method the website's contents and it will provide a list of specializations or ["ERROR"] if it can't find anything
        String promptInstructions = properties.promptInstructions();
        String prompt = promptInstructions + profileContents;
        String parsedOutput = parseParagraph(prompt);
        if (parsedOutput.contains("ERROR")) {
            throw new BogusProfileException();
        }
        int startIndex = parsedOutput.indexOf("[");
        int endIndex = parsedOutput.indexOf("]") + 1;
        String[] array = parsedOutput.substring(startIndex, endIndex).split(", ");
        for (int i = 0; i < array.length; i++) {
            array[i] = array[i].replaceAll("'", "");
        }
        log.debug("Parsed output is: {}", parsedOutput);
        return ProfileInfo.fromResponseArray(array);
    }
    
    /**
     * @param prompt
     * @return
     * @throws Exception
     */
    private String parseParagraph(String prompt) throws Exception { //helper method to the AI parser
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.apiKey());
        OpenAiRequest body = new OpenAiRequest(properties.model(), prompt);
        RequestEntity<OpenAiRequest> request = RequestEntity.post(properties.apiUrl())
                .headers(headers)
                .body(body);
        String response = restTemplate.exchange(request, String.class).getBody();
        log.debug(response);
        return extractFromResponse(response);
    }

    /**
     * @param response
     * @return
     * @throws JsonProcessingException
     * @throws JsonMappingException
     */
    private String extractFromResponse(String response) throws JsonMappingException, JsonProcessingException {
        JsonNode responseNode = objectMapper.readTree(response);
        JsonNode choices = responseNode.path("choices");
        JsonNode message = choices.path(0).path("message");
        String content = message.path("content").asText().trim();
        return content;
    }
}
