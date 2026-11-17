package vn.tsolve.identity.dto;

import java.time.Instant;
import vn.tsolve.common.security.TSolveUser;

/** An Active Account linked to a ticket tracker account, and when the link was made (AC-001.77). */
public record LinkedAccount(TSolveUser user, Instant linkedAt) {}
