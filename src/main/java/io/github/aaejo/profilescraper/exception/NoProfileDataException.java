package io.github.aaejo.profilescraper.exception;

import io.github.aaejo.messaging.records.Profile;

/**
 * @author Omri Harary
 */
public class NoProfileDataException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Profile includes no url or htmlContent to extract details from.";

    public NoProfileDataException(Profile profile) {
        super(MESSAGE_TEMPLATE);
    }
}
