package io.github.aaejo.profilescraper.exception;

/**
 * @author Omri Harary
 */
public class ProfileDetailsProcessingException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Unable to extract details from profile data.";

    public ProfileDetailsProcessingException() {
        super(MESSAGE_TEMPLATE);
    }

    public ProfileDetailsProcessingException(Throwable cause) {
        super(MESSAGE_TEMPLATE, cause);
    }
}
