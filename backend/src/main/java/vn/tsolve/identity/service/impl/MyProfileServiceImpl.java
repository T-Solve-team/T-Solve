package vn.tsolve.identity.service.impl;

import java.text.Collator;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.MySolution;
import vn.tsolve.common.session.dto.MySolutionFilter;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;
import vn.tsolve.common.session.lookup.WrittenTicketLookup;
import vn.tsolve.identity.dto.MySolutionsQuery;
import vn.tsolve.identity.service.MyProfileService;

/** The lookups are optional: until the owning module provides a bean, the answer is empty (AC-003.5, AC-003.8). */
@Service
public class MyProfileServiceImpl implements MyProfileService {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final ZoneId WORKSPACE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ObjectProvider<ViewableSpaceLookup> spaces;
    private final ObjectProvider<WrittenTicketLookup> solutions;

    public MyProfileServiceImpl(
            ObjectProvider<ViewableSpaceLookup> spaces, ObjectProvider<WrittenTicketLookup> solutions) {
        this.spaces = spaces;
        this.solutions = solutions;
    }

    @Override
    public List<SpaceRef> getMyViewableSpaces() {
        var accountId = TSolveUser.current().orElseThrow().id();
        var lookup = spaces.getIfAvailable();
        if (lookup == null) {
            return List.of();
        }
        // A Collator is not thread-safe, so one per call.
        Comparator<SpaceRef> byName = Comparator.comparing(SpaceRef::name, Collator.getInstance(VIETNAMESE));
        return lookup.findViewableSpaces(accountId).stream()
                .sorted(byName.thenComparing(SpaceRef::spaceId))
                .toList();
    }

    @Override
    public Page<MySolution> getMySolutions(MySolutionsQuery query) {
        var accountId = TSolveUser.current().orElseThrow().id();
        var pageable = PageRequest.of(query.page(), query.size());
        var lookup = solutions.getIfAvailable();
        if (lookup == null) {
            return Page.empty(pageable);
        }
        // Whole Workspace days: from 00:00 of `from` (inclusive) to 00:00 of the day after `to` (exclusive).
        var filter = new MySolutionFilter(
                query.status(),
                query.projectId(),
                query.from() == null
                        ? null
                        : query.from().atStartOfDay(WORKSPACE_ZONE).toInstant(),
                query.to() == null
                        ? null
                        : query.to().plusDays(1).atStartOfDay(WORKSPACE_ZONE).toInstant());
        return lookup.findSolutions(accountId, filter, pageable);
    }
}
