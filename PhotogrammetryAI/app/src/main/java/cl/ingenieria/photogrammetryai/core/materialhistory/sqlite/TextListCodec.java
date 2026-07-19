package cl.ingenieria.photogrammetryai.core.materialhistory.sqlite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Compact delimiter-safe codec for candidate reasons and warnings stored in SQLite TEXT fields. */
public final class TextListCodec {
    private TextListCodec() {}

    public static String encode(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append('.');
            result.append(encoder.encodeToString(value.trim().getBytes(StandardCharsets.UTF_8)));
        }
        return result.toString();
    }

    public static List<String> decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return Collections.emptyList();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        List<String> result = new ArrayList<>();
        for (String token : encoded.split("\\.")) {
            if (token.isEmpty()) continue;
            result.add(new String(decoder.decode(token), StandardCharsets.UTF_8));
        }
        return Collections.unmodifiableList(result);
    }
}
