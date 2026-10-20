package vn.tsolve.ticket.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ApproveTicketsRequest(
        @JsonDeserialize(contentUsing = IntegralTicketIdDeserializer.class) @NotEmpty(message = "MSG01")
                List<@NotNull(message = "MSG01") @Positive(message = "MSG06") Long> ticketIds) {}
