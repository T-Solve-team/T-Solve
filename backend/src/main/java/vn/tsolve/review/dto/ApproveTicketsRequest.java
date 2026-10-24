package vn.tsolve.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApproveTicketsRequest(
        @JsonDeserialize(contentUsing = IntegralTicketIdDeserializer.class)
                @NotEmpty(message = "MSG01")
                @Size(max = 500, message = "MSG06")
                List<@NotNull(message = "MSG01") @Positive(message = "MSG06") Long> ticketIds) {}
