package vn.tsolve.wiki.service.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.session.dto.SpaceRef;
import vn.tsolve.common.session.lookup.ManagedSpaceLookup;
import vn.tsolve.common.session.lookup.ViewableSpaceLookup;
import vn.tsolve.wiki.repository.AccessRepository;

@Service
@Transactional(readOnly = true)
public class WikiSpaceLookups implements ManagedSpaceLookup, ViewableSpaceLookup {
    private final AccessRepository repository;

    public WikiSpaceLookups(AccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SpaceRef> findManagedSpaces(UUID userId) {
        return repository.findManagedSpaces(userId).stream()
                .map(row -> new SpaceRef(row.getSpaceId(), row.getName()))
                .toList();
    }

    @Override
    public List<SpaceRef> findViewableSpaces(UUID userId) {
        return repository.findViewableSpaces(userId).stream()
                .map(row -> new SpaceRef(row.getSpaceId(), row.getName()))
                .toList();
    }
}
