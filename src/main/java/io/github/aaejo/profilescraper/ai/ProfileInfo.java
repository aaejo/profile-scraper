package io.github.aaejo.profilescraper.ai;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

public record ProfileInfo(String name, String email, String[] specializations) {

    /**
     * Bridge from old return type, pending further refactoring
     */
    public static ProfileInfo fromResponseArray(String[] oldStyleArray) {
        String name = StringUtils.removeStart(oldStyleArray[0], "[");
        String email = oldStyleArray[1];
        String[] specs = Arrays.copyOfRange(oldStyleArray, 2, oldStyleArray.length);
        for (int i = 0; i < specs.length; i++) {
            String spec = specs[i];
            spec = StringUtils.removeStart(spec, "[");
            spec = StringUtils.removeEnd(spec, "]");
            specs[i] = spec;
        }

        return new ProfileInfo(
                StringUtils.equalsIgnoreCase(name, "null") ? null : name,
                StringUtils.equalsIgnoreCase(email, "null") ? null : email,
                StringUtils.equalsIgnoreCase(specs[0], "null") ? null
                        // Dedupe specializations, but only if we really need to
                        : Arrays.stream(specs).distinct().toList().toArray(new String[0]));
    }
}
