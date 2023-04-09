package io.github.aaejo.profilescraper.ai;

public record OpenAiRequest(String model, Message[] messages, double temperature) {

    /**
     * More convenient constructor for our purposes
     * @param model
     * @param content
     * @param temperature
     */
    public OpenAiRequest(String model, String content) {
        this(model, new Message[]{new Message("user", content)}, 0.0);
    }

    public record Message(String role, String content) {
    }
}
