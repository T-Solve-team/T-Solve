package vn.tsolve.identity.service.impl;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;
import vn.tsolve.identity.service.MyProfileService;

/** The lookups are optional: until the owning module provides a bean, the answer is empty (AC-003.5). */
@Service
public class MyProfileServiceImpl implements MyProfileService {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private final ObjectProvider<ViewableSpaceLookup> spaces;

    public MyProfileServiceImpl(ObjectProvider<ViewableSpaceLookup> spaces) {
        this.spaces = spaces;
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
}
