package io.github.aaejo.profilescraper.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import io.github.aaejo.profilescraper.ai.configuration.OpenAiClientProperties;
import io.github.aaejo.profilescraper.exception.BogusProfileException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Aidan Richards
 */
@Slf4j
@Component
public class SpecializationsProcessor {

    private final RestTemplate restTemplate;
    private final OpenAiClientProperties properties;

    /**
     * @param properties
     */
    public SpecializationsProcessor(RestTemplateBuilder restTemplateBuilder, OpenAiClientProperties properties) {
        this.restTemplate = restTemplateBuilder.build();
        this.properties = properties;
    }

    /**
     * @param promptContents
     * @return
     */
    public String[] getSpecializations(String promptContents) throws Exception { //give this method the website's contents and it will provide a list of specializations or ["ERROR"] if it can't find anything
        String promptInstructions = properties.promptInstructions();
        String prompt = promptInstructions + promptContents;
        String[] array = new String[0];
        String parsedOutput = parseParagraph(prompt);
        if (parsedOutput.contains("ERROR")) {
            throw new BogusProfileException();
        }
        int startIndex = parsedOutput.indexOf("[");
        int endIndex = parsedOutput.indexOf("]") + 1;
        array = parsedOutput.substring(startIndex, endIndex).split(", ");
        for (int i = 0; i < array.length; i++) {
            array[i] = array[i].replaceAll("'", "");
        }
        log.debug("Parsed output is: {}", parsedOutput);
        return array;
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
     */
    private static String extractFromResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray choices = jsonObject.getJSONArray("choices");
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message.getString("content").trim();
        return content;
    }
}
