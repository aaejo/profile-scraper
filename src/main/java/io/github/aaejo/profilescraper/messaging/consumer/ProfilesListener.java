package io.github.aaejo.profilescraper.messaging.consumer;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.finder.client.FinderClientResponse;
import io.github.aaejo.messaging.records.IncompleteScrape;
import io.github.aaejo.messaging.records.IncompleteScrape.MissingFlags;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.messaging.records.Reviewer;
import io.github.aaejo.profilescraper.ai.ProfileInfo;
import io.github.aaejo.profilescraper.ai.ProfileProcessor;
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
    private final ProfileProcessor specializationsProcessor;
    private final ReviewersDataProducer reviewersDataProducer;
    private final ManualInterventionProducer manualInterventionProducer;
    private final EmailValidator emailValidator;

    public ProfilesListener(FinderClient client, ProfileProcessor specializationsProcessor,
            ReviewersDataProducer reviewersDataProducer, ManualInterventionProducer manualInterventionProducer) {
        this.client = client;
        this.specializationsProcessor = specializationsProcessor;
        this.reviewersDataProducer = reviewersDataProducer;
        this.manualInterventionProducer = manualInterventionProducer;
        this.emailValidator = EmailValidator.getInstance();
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
            // TODO: sometimes the link is to their own site, that's generally fine, but sometimes it's to amazon or something?
            // ie it's not a profile link, it's just a book link embedded. Need to catch that.
            FinderClientResponse response = client.get(profile.url());
            if (response == null || response.document() == null || !response.isSuccess()) {
                log.error("Failed to fetch profile page from {}. May attempt htmlContent as fallback.", profile.url());

                if (StringUtils.isNotBlank(profile.htmlContent())) {
                    profileData = Parser.parseBodyFragment(profile.htmlContent(), profile.institution().website());
                } else {
                    if (response.exception().isPresent()) {
                        throw new ProfileDetailsProcessingException(response.exception().get());
                    } else {
                        throw new ProfileDetailsProcessingException();
                    }
                }
            } else {
                profileData = response.document();
            }
        } else if (StringUtils.isNotBlank(profile.htmlContent())) {
            profileData = Parser.parseBodyFragment(profile.htmlContent(), profile.institution().website());
        } else {
            log.error("Profile includes no url or htmlContent to extract details from.");
            throw new NoProfileDataException(profile);
        }

        // Retrieving name, email and specializations of reviewer
        ProfileInfo info;
        String contents = drillDownToContent(profileData).text();
        try {
            info = specializationsProcessor.getSpecializations(contents);
        } catch (BogusProfileException e) {
            log.error("Profile scraper malfunctioned. Bogus profile discarded.");
            throw e;
        } catch (Exception e) {
            log.error("Unable to process profile data.", e);
            throw new ProfileDetailsProcessingException(e);
        }

        // Creating Reviewer object
        List<MissingFlags> missing = new ArrayList<MissingFlags>();

        if (info.name() == null) {
            missing.add(MissingFlags.NAME);
        }

        if (info.email() == null || !emailValidator.isValid(info.email())) {
            // TODO: we should actually keep track of what invalid emails are returned
            missing.add(MissingFlags.EMAIL);
        }

        if (info.specializations() == null) {
            missing.add(MissingFlags.SPECS);
        }

        Reviewer r = new Reviewer(info.name(), "Dr.", info.email(), profile.institution(), profile.department(),
                info.specializations());

        // If any missing flags are set, send to manualInterventionProducer
        // Otherwise, send to reviewersDataProducer
        if (!missing.isEmpty()) {
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

    /**
     * Modified from profile-finder
     * @param page
     * @return
     */
    private Element drillDownToContent(Document page) {
        List<Element> drillDown = new ArrayList<>();
        drillDown.add(page.body());

        Element skipAnchor = page.selectFirst("a[id=main-content]:empty");
        Element mainContentBySkipAnchor = skipAnchor != null ? skipAnchor.parent() : null;
        if (mainContentBySkipAnchor != null) {
            log.debug("Found main content of {} by using skip anchor", page.location());
            drillDown.add(mainContentBySkipAnchor);
        }

        Element mainContentByMainTag = page.selectFirst("main");
        if (mainContentByMainTag != null) {
            log.debug("Found main content of {} by HTML main tag", page.location());
            drillDown.add(mainContentByMainTag);
        }

        Element mainContentByAriaRole = page.selectFirst("*[role=main]");
        if (mainContentByAriaRole != null) {
            log.debug("Found main content of {} by main ARIA role", page.location());
            drillDown.add(mainContentByAriaRole);
        }

        Element mainContentByIdMain = page.getElementById("main");
        if (mainContentByIdMain != null && mainContentByIdMain.tag().isBlock()) {
            log.debug("Found main content of {} by id = main", page.location());
            drillDown.add(mainContentByIdMain);
        }

        Element mainContentByIdContent = page.getElementById("content");
        if (mainContentByIdContent != null && mainContentByIdContent.tag().isBlock()) {
            log.debug("Found main content of {} by id = content", page.location());
            drillDown.add(mainContentByIdContent);
        }

        Element mainContentByIdMainContent = page.getElementById("main-content");
        if (mainContentByIdMainContent != null && mainContentByIdMainContent.tag().isBlock()) {
            log.debug("Found main content of {} by id = main-content", page.location());
            drillDown.add(mainContentByIdMainContent);
        }

        Element content = drillDown.stream()
                .distinct()
                .filter(e -> StringUtils.isNotBlank(e.text()))
                .sorted(Comparator.<Element>comparingInt(e -> e.parents().size()).reversed())
                .findFirst().get();
        
        if (content.tagName().equals("body")) {
            if (!content.getElementsByTag("header").isEmpty() || !content.getElementsByTag("footer").isEmpty()) {
                // Deep clone to not be modifying the original fetched page
                content = content.clone();
                // Select first header element, assumed to be page header (rarely more than one but possible)
                Element pageHeader = content.select("header").first();
                // Select last footer element, assumed to be page footer (rarely more than one but possible)
                Element pageFooter = content.select("footer").last();

                if (pageHeader != null) {
                    pageHeader.remove();
                }

                if (pageFooter != null) {
                    pageFooter.remove();
                }
            }
        }

        return content;
    }
}
