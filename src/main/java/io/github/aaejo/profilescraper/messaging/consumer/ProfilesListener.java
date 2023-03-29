package io.github.aaejo.profilescraper.messaging.consumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.springframework.boot.context.config.Profiles;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.messaging.records.Reviewer;
import io.github.aaejo.messaging.records.IncompleteScrape.MissingFlags;
import io.github.aaejo.profilescraper.messaging.producer.ManualInterventionProducer;
import io.github.aaejo.profilescraper.messaging.producer.ReviewersDataProducer;
import io.github.aaejo.messaging.records.IncompleteScrape;
import lombok.extern.slf4j.Slf4j;

//the following imports are the for AI parser
import java.util.Arrays;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.json.JSONArray;
import org.json.JSONObject;
//import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@KafkaListener(id = "profile-scraper", topics = "profiles")
public class ProfilesListener {

    private static final String OPENAI_API_KEY = "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"; //used for AI parser
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/completions"; //used for AI parser

    private final FinderClient client;
    private final ReviewersDataProducer reviewersDataProducer;
    private final ManualInterventionProducer manualInterventionProducer;

        public ProfilesListener(FinderClient client, ReviewersDataProducer reviewersDataProducer, ManualInterventionProducer manualInterventionProducer) {
            this.client = client;
            this.reviewersDataProducer = reviewersDataProducer;
            this.manualInterventionProducer = manualInterventionProducer;
        }
    
        public static String[] getSpecializations(String promptContents) { //give this method the website's contents and it will provide a list of specializations or ["ERROR"] if it can't find anything
            String promptInstructions = "The following text is the contents of a person's profile on a website. They are a philosophy department faculty member. Create a Java array containing person's philosophy specializations from the following text. Each catagory must be a generic philosophy specialization. Use as few catagories as possible. Do not list specializations that are not generic and widely known philosophy areas and return ['ERROR'] if there aren't any specializations in the Bio paragraph. Here's the website contents for this person: ";
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
                array = new String[]{"ERROR"};
            }
            return array;
        }

        private static String parseParagraph(String prompt) throws Exception { //helper method to the AI parser
            String modelName = "text-davinci-003";
            String requestBody = "{\"model\": \"" + modelName + "\",\"prompt\": \"" + prompt + "\",\"max_tokens\":50,\"temperature\":0.0,\"n\":1}";
            String response = Request.post(OPENAI_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .bodyString(requestBody, ContentType.APPLICATION_JSON)
                .execute()
                .returnContent()
                .asString();
            return extractFromResponse(response);
        }
        
        private static String extractFromResponse(String response) { //helper method to the AI parser
            String jsonString = response;
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray choicesArr = jsonObject.getJSONArray("choices");
            JSONObject choiceObj = choicesArr.getJSONObject(0);
            String parsed = choiceObj.getString("text").trim();
            return parsed;	
        }

    @KafkaHandler
    public void handle(Profile profile) {
        // profile includes the following fields:
            // String htmlContent, String url, String department, Institution institution
        
        // reviewer includes the following fields:
            // String name, String salutation, String email, Institution institution, String department, String[] specializations

        log.info(profile.toString());

        // Creating Reviewer object
        Reviewer r = new Reviewer(null, null, null, profile.institution(), profile.department(), null);
        String reviewerEmail = null;
        String reviewerName = null;

        // Gathering data
        List<Node> facultyEntry = Parser.parseFragment(profile.htmlContent(), null, profile.institution().website());
        Document url = client.get(profile.url());
        url.text();

        // Finding email of reviewer
        Pattern p = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");
        Matcher matcher = p.matcher(url.text());
        Set<String> emails = new HashSet<String>();
        while (matcher.find()) {
            // Only includes emails that do not start with "enquiries", "inquiries", "info", "contact", or "philosophy"
            if (!(matcher.group().startsWith("enquiries")) && !(matcher.group().startsWith("info")) && !(matcher.group().startsWith("contact")) && !(matcher.group().startsWith("philosophy")) && !(matcher.group().startsWith("inquiries"))) {
                emails.add(matcher.group());
            }
        }
        if (emails.size() == 1) {
            reviewerEmail = emails.iterator().next();
        }

        // Finding name of reviewer
        String facultyEntryText = facultyEntry.get(0).toString();
        Pattern p2 = Pattern.compile(">([a-zA-Z]+\\s[a-zA-Z]+)<");
        Matcher matcher2 = p2.matcher(facultyEntryText);
        Set<String> names = new HashSet<String>();
        while (matcher2.find()) {
            names.add(matcher2.group(1));
        }
        if (names.size() == 1) {
            reviewerName = names.iterator().next();
        }

        // Finding specializations of reviewer
        String[] specializations = getSpecializations(url.text());
        if (specializations[0].equals("ERROR")) {
            specializations = null;
        }

        // If any element in r is null, send to manualInterventionProducer
        // Otherwise, send to reviewersDataProducer
        r = new Reviewer(reviewerName, "Dr.", reviewerEmail, profile.institution(), profile.department(), specializations);
        List<MissingFlags> missing = new ArrayList<MissingFlags>();
        
        if ((r.name() == null) || (r.email() == null) || (r.specializations() == null)) {
            if (r.name() == null) {
                missing.add(MissingFlags.NAME);
            }
            if (r.email() == null) {
                missing.add(MissingFlags.EMAIL);
            }
            if (r.specializations() == null) {
                missing.add(MissingFlags.SPECS);
            }
            // send to manual intervention
            MissingFlags[] flags = new MissingFlags[missing.size()];
            flags = missing.toArray(flags);
            IncompleteScrape incomplete = new IncompleteScrape(profile, r, flags);
            manualInterventionProducer.send(incomplete);
        }
        else {
            reviewersDataProducer.send(r);
        }
        
    }
}
