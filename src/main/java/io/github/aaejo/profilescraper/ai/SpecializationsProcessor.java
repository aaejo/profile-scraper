package io.github.aaejo.profilescraper.ai;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import io.github.aaejo.profilescraper.ai.configuration.OpenAiClientProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * 
 * @author Aidan Richards
 */
@Slf4j
@Component
public class SpecializationsProcessor {

    private final OpenAiClientProperties properties;

    /**
     * @param properties
     */
    public SpecializationsProcessor(OpenAiClientProperties properties) {
        this.properties = properties;
    }

    /**
     * @param promptContents
     * @return
     */
    public String[] getSpecializations(String promptContents) { //give this method the website's contents and it will provide a list of specializations or ["ERROR"] if it can't find anything
        String promptInstructions = properties.promptInstructions();
        String prompt = promptInstructions + promptContents;
        String[] array = new String[0];
        try {
            String parsedOutput = parseParagraph(prompt);
            int startIndex = parsedOutput.indexOf("[") + 1;
            int endIndex = parsedOutput.indexOf("]");
            array = parsedOutput.substring(startIndex, endIndex).split(", ");
            for (int i = 0; i < array.length; i++) {
                array[i] = array[i].replaceAll("'", "");
            }
        } catch (Exception e) {
            log.error("An error occurred in getting specializations", e);
            array = new String[]{"ERROR"};
        }
        return array;
    }

    /**
     * @param prompt
     * @return
     * @throws Exception
     */
    private String parseParagraph(String prompt) throws Exception { //helper method to the AI parser
        String modelName = properties.model();
        String requestBody = "{\"model\": \"" + modelName + "\",\"prompt\": \"" + prompt + "\",\"max_tokens\":50,\"temperature\":0.0,\"n\":1}";
        String response = Request.post(properties.apiUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + properties.apiKey())
                .bodyString(requestBody, ContentType.APPLICATION_JSON)
                .execute()
                .returnContent()
                .asString();
        return extractFromResponse(response);
    }
    
    /**
     * @param response
     * @return
     */
    private String extractFromResponse(String response) { //helper method to the AI parser
        String jsonString = response;
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray choicesArr = jsonObject.getJSONArray("choices");
        JSONObject choiceObj = choicesArr.getJSONObject(0);
        String parsed = choiceObj.getString("text").trim();
        return parsed;	
    }
}
