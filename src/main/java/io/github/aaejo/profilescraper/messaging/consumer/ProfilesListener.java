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
            System.out.println("rrrrrrrrrrrrrrrrraw input text: " + profileData.text());
            //info = specializationsProcessor.getSpecializations(profileData.text());
            info = specializationsProcessor.getSpecializations("Otago Philosophy Ellis Philosophy PROGRAMME EVENTS POSTGRADUATE UNDERGRADUATE STAFF CONTACT HISTORY TUTORS LISA ELLIS PROFESSOR Director, Philosophy, Politics, and Economics Programme PhD Berkeley (1999) MA Berkeley (1992) BA Princeton (1990) office: 2C14, second floor, central corridor, Burns Building, 95 Albany Street office hours for semester 1 2022: Wednesdays 2-4 phone: +64 (0)3 479 8727 email: RESEARCH TEACHING academia.edu Twitter Curriculum Vitae Lisa Ellis is Professor of Philosophy and Director of the Philosophy, Politics, and Economics programme at the University of Otago. Lisa?s work investigates how we can make policy decisions that serve our interests in flourishing now and in the future. Her current project, ?the collective implications of discrete decisions,? includes papers in environmental democracy, the collective ethics of flying, the value of biodiversity losses, climate adaptation justice, and species extinction. See her research page for links to Lisa?s publications. Lisa is past president of the Association for Political Theory, former section editor of the Journal of Politics, and a current editor of Political Theory. Lisa?s work has been supported by the Institute for Advanced Study (Princeton), the National Endowment for the Humanities, the Alexander von Humboldt Foundation, the Andrew W. Mellon Foundation, the Deutscher Akademischer Austauschdienst, and New Zealand?s Deep South National Science Challenge. Read about Lisa?s recent work: NZ Herald, on anti-natalism, Guardian, on emissions reduction, Newsroom, ?Playing chicken with the government?; Stuff, ?Beach Road?; Stuff, ?Climate Myths Debunked? Hear about Lisa?s recent work: research seminar, 'Maximum Scholarly Value for Minimal Harm: Practical Climate Ethics for Academics' (recording here); Deep South webinar on adaptation, 95bfm on climate cooperation, Dialogues podcast from Snodger Media; Radio New Zealand, ?Sea-Level Rise Threat?; Lisa talks to Jesse Mulligan on RNZ Afternoons PROGRAMME staff contact history tutors EVENTS seminar fellows lectures conferences POSTGRADUATE qualifications students seminar placement UNDERGRADUATE qualifications papers club careers");
        } catch (BogusProfileException e) {
            log.error("Profile scrapper malfunctioned. Bogus profile discarded.");
            throw new BogusProfileException(profile);
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
