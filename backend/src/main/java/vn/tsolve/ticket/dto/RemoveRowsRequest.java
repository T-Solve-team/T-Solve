package vn.tsolve.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RemoveRowsRequest(@NotEmpty List<@NotNull @Min(1) Integer> rowNumbers) {}
