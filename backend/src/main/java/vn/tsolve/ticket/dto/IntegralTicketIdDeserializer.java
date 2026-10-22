package vn.tsolve.ticket.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;

/** Jackson's default float-to-long coercion could silently decide a different ticket (BUG-008). */
public class IntegralTicketIdDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) throw JsonMappingException.from(parser, "MSG06");
        return parser.getLongValue();
    }
}
