package io.github.aaejo.profilescraper.messaging.consumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
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

@Slf4j
@Component
@KafkaListener(id = "profile-scraper", topics = "profiles")
public class ProfilesListener {

    private final FinderClient client;
    private final ReviewersDataProducer reviewersDataProducer;
    private final ManualInterventionProducer manualInterventionProducer;

        public ProfilesListener(FinderClient client, ReviewersDataProducer reviewersDataProducer, ManualInterventionProducer manualInterventionProducer) {
            this.client = client;
            this.reviewersDataProducer = reviewersDataProducer;
            this.manualInterventionProducer = manualInterventionProducer;
        }
    
    @KafkaHandler
    public void handle(Profile profile) {
        // profile includes the following fields:
            // - String htmlContent
            // - String url
            // - String department
            // - Institution institution
        
        // reviewer includes the following fields:
            // - String name
            // - String salutation (ex. Dr)
            // - String email
            // - Institution institution
            // - String department
            // - String[] specializations

        log.info(profile.toString());

        // New Reviewer object
        Reviewer r = new Reviewer(null, null, null, profile.institution(), profile.department(), null);
        
        // Gathering data
        List<Node> facultyEntry = Parser.parseFragment(profile.htmlContent(), null, profile.institution().website());
        Document url = client.get(profile.url());
        url.text();


        // Finding email of reviewer
        String reviewerEmail = null;
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


        // Finding name of reviewer through facultyEntry and using regex to find name
        String reviewerName = null;
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


        // Retrieving name
        //String reviewerName = null;
        //Elements name = url.select("h1");
        //reviewerName = name.get(0).text();
        //r = new Reviewer(reviewerName, "Dr.", reviewerEmail, profile.institution(), profile.department(), null);
        //System.out.println(r);

       

        // Check that every element in r is not null
        // If any element is null, send to manual intervention
        // Else, send to reviewers-data topic
        r = new Reviewer(reviewerName, "Dr.", reviewerEmail, profile.institution(), profile.department(), null);
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
