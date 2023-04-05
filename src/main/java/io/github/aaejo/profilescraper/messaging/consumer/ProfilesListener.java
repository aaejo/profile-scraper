package io.github.aaejo.profilescraper.messaging.consumer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.IncompleteScrape;
import io.github.aaejo.messaging.records.IncompleteScrape.MissingFlags;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.messaging.records.Reviewer;
import io.github.aaejo.profilescraper.ai.SpecializationsProcessor;
import io.github.aaejo.profilescraper.messaging.producer.ManualInterventionProducer;
import io.github.aaejo.profilescraper.messaging.producer.ReviewersDataProducer;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Eileen Li
 * @author Omri Harary
 */
@Slf4j
@Component
@KafkaListener(id = "profile-scraper", topics = "profiles")
public class ProfilesListener {

    private final FinderClient client;
    private final SpecializationsProcessor specializationsProcessor;
    private final ReviewersDataProducer reviewersDataProducer;
    private final ManualInterventionProducer manualInterventionProducer;

    public ProfilesListener(FinderClient client, SpecializationsProcessor specializationsProcessor,
            ReviewersDataProducer reviewersDataProducer, ManualInterventionProducer manualInterventionProducer) {
        this.client = client;
        this.specializationsProcessor = specializationsProcessor;
        this.reviewersDataProducer = reviewersDataProducer;
        this.manualInterventionProducer = manualInterventionProducer;
    }

    @KafkaHandler
    public void handle(Profile profile) {
        // profile includes the following fields:
            // String htmlContent, String url, String department, Institution institution
        
        // reviewer includes the following fields:
            // String name, String salutation, String email, Institution institution, String department, String[] specializations

        log.debug("Received profile {}", profile);
        
        // Gathering data
        Document url = client.get(profile.url()); // Need to check if there is a profile URL

        // Retrieving name, email and specializations of reviewer
        String[] info = specializationsProcessor.getSpecializations(url.text());
        
        // If info returns "ERROR", discard profile
        if (info.length == 1) {
            return;
        }

        // Creating Reviewer object
        String reviewerName = info[0].substring(1);
        String reviewerEmail = info[1];
        String[] reviewerSpec = Arrays.copyOfRange(info, 2, info.length);
        if (reviewerSpec.length == 1) {
            reviewerSpec[0] = reviewerSpec[0].substring(1, reviewerSpec[0].length()-1);
        }
        else {
            reviewerSpec[0] = reviewerSpec[0].substring(1);
            reviewerSpec[reviewerSpec.length-1] = reviewerSpec[reviewerSpec.length-1].substring(0, reviewerSpec[reviewerSpec.length-1].length()-1);
        }
        if (reviewerSpec[0].equals("null")) {
            reviewerSpec = null;
        }
        Reviewer r = new Reviewer(reviewerName, "Dr.", reviewerEmail, profile.institution(), profile.department(), reviewerSpec);
        
        // If any element in r is null, send to manualInterventionProducer
        // Otherwise, send to reviewersDataProducer
        List<MissingFlags> missing = new ArrayList<MissingFlags>();
        if ((r.name() == "null") || (r.email() == "null") || (r.specializations() == null)) {
            if (r.name() == "null") {
                missing.add(MissingFlags.NAME);
            }
            if (r.email() == "null") {
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
