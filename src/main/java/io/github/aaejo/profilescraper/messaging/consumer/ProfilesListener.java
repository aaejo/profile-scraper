package io.github.aaejo.profilescraper.messaging.consumer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.IncompleteScrape;
import io.github.aaejo.messaging.records.IncompleteScrape.MissingFlags;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.messaging.records.Reviewer;
import io.github.aaejo.profilescraper.ai.SpecializationsProcessor;
import io.github.aaejo.profilescraper.exception.BogusProfileException;
import io.github.aaejo.profilescraper.exception.NoProfileDataException;
import io.github.aaejo.profilescraper.exception.ProfileDetailsProcessingException;
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
        log.info("Profile recieved from {} in {}", profile.institution().name(), profile.institution().country());
        log.debug("Received profile {}", profile);

        // Gathering data
        Document profileData;
        if (StringUtils.isNotBlank(profile.url())) {
            profileData = client.get(profile.url());
        } else if (StringUtils.isNotBlank(profile.htmlContent())) {
            profileData = Parser.parseBodyFragment(profile.htmlContent(), profile.institution().website());
        } else {
            log.error("Profile includes no url or htmlContent to extract details from.");
            throw new NoProfileDataException(profile);
        }

        // Retrieving name, email and specializations of reviewer
        String[] info;
        try {
            info = specializationsProcessor.getSpecializations(profileData.text());
        } catch (BogusProfileException e) {
            log.error("Profile scrapper malfunctioned. Bogus profile discarded.");
            throw e;
        } catch (Exception e) {
            log.error("Unable to process profile data.", e);
            throw new ProfileDetailsProcessingException(profile, e);
        }

        // Creating Reviewer object
        String reviewerName = StringUtils.removeStart(info[0], "[");
        String reviewerEmail = info[1];
        String[] reviewerSpec = Arrays.copyOfRange(info, 2, info.length);
        for (int i = 0; i < reviewerSpec.length; i++) {
            String spec = reviewerSpec[i];
            spec = StringUtils.removeStart(spec, "[");
            spec = StringUtils.removeEnd(spec, "]");
            reviewerSpec[i] = spec;
        }

        if (reviewerSpec[0].equals("null")) {
            reviewerSpec = null;
        }
        Reviewer r = new Reviewer(reviewerName, "Dr.", reviewerEmail, profile.institution(), profile.department(), reviewerSpec);

        // If any element in r is null, send to manualInterventionProducer
        // Otherwise, send to reviewersDataProducer
        List<MissingFlags> missing = new ArrayList<MissingFlags>();
        if (StringUtils.equalsIgnoreCase(r.name(), "null") || StringUtils.equalsIgnoreCase(r.email(), "null") || r.specializations() == null) {
            if (StringUtils.equalsIgnoreCase(r.name(), "null")) {
                missing.add(MissingFlags.NAME);
            }
            if (StringUtils.equalsIgnoreCase(r.email(), "null")) {
                missing.add(MissingFlags.EMAIL);
            }
            if (r.specializations() == null) {
                missing.add(MissingFlags.SPECS);
            }
            // send to manual intervention
            MissingFlags[] flags = new MissingFlags[missing.size()];
            flags = missing.toArray(flags);
            IncompleteScrape incomplete = new IncompleteScrape(profile, r, flags);
            log.info("Unable to complete profile scrape, sending to manual intervention");
            manualInterventionProducer.send(incomplete);
        } else {
            log.info("Reviewer data extraction complete");
            reviewersDataProducer.send(r);
        }

    }
}
