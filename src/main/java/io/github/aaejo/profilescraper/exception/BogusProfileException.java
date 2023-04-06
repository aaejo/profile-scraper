package io.github.aaejo.profilescraper.exception;

import io.github.aaejo.messaging.records.Profile;

/**
 * @author Omri Harary
 */
public class BogusProfileException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "The scrapped profile wasn't a profile at all. Bogus text.";

    private Profile profile;

    public BogusProfileException(Profile profile) {
        super(MESSAGE_TEMPLATE);

        this.profile = profile;
    }
}
