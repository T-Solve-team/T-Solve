package vn.tsolve.ticket.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequestChangesRequest(
        @JsonDeserialize(using = IntegralTicketIdDeserializer.class)
                @NotNull(message = "MSG01")
                @Positive(message = "MSG06")
                Long ticketId,
        @NotBlank(message = "MSG01") @Size(max = 1000, message = "MSG02") String comment) {}
