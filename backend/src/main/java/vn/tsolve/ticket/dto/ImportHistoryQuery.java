package vn.tsolve.ticket.dto;

import java.time.LocalDate;
import java.util.UUID;
import vn.tsolve.ticket.entity.TicketSource;

public record ImportHistoryQuery(
        UUID projectId, TicketSource source, LocalDate dateFrom, LocalDate dateTo, Integer page, Integer size) {}
