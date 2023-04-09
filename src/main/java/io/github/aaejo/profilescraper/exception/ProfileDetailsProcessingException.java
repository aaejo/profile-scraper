package io.github.aaejo.profilescraper.exception;

import io.github.aaejo.messaging.records.Profile;

/**
 * @author Omri Harary
 */
public class ProfileDetailsProcessingException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Unable to extract details from profile data.";

    private Profile profile;

    public ProfileDetailsProcessingException(Profile profile, Throwable cause) {
        super(MESSAGE_TEMPLATE, cause);

        this.profile = profile;
    }
}
