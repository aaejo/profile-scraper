package io.github.aaejo.profilescraper.exception;

/**
 * @author Aidan Richards
 */
public class BogusProfileException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "The scraped profile wasn't a profile at all. Bogus text.";

    public BogusProfileException() {
        super(MESSAGE_TEMPLATE);
    }
}
