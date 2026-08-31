package com.graphqlguy.schemanav.tokens;

import com.graphqlguy.schemanav.config.SchemaNavProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/**
 * The single token yardstick for the whole instrument.
 *
 * A "token" is tokenizer-specific: OpenAI, Anthropic, and Qwen models each slice text
 * differently, so there is no universal count. The instrument therefore pins ONE encoding
 * (configured in schemanav.tokens.encoding) and measures everything with it. The numbers
 * are comparable with each other, which is what the experiments need; they approximate,
 * but do not equal, what any specific provider will bill.
 */
@Component
public class TokenMeter {

    private final Encoding encoding;
    private final String encodingName;

    public TokenMeter(SchemaNavProperties properties) {
        this.encodingName = properties.getTokens().getEncoding();
        this.encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.valueOf(encodingName));
    }

    public int count(String text) {
        return encoding.countTokens(text);
    }

    public String encodingName() {
        return encodingName;
    }

    /** The footer appended to every payload the instrument prints. */
    public String footer(String text) {
        return "[" + count(text) + " tokens, " + encodingName + "]";
    }
}
