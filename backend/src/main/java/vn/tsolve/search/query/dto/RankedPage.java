package vn.tsolve.search.query.dto;

import org.springframework.data.domain.Page;

public record RankedPage(Page<TicketSearchResultResponse> page, boolean isFallback) {}
