package vn.tsolve.search.query.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RankFilter(UUID departmentId, String source, LocalDate resolvedFrom, LocalDate resolvedTo) {}
