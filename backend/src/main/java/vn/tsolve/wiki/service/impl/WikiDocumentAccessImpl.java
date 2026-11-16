package vn.tsolve.wiki.service.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.triage.service.WikiDocumentAccess;
import vn.tsolve.wiki.repository.AccessRepository;

@Service
@Transactional(readOnly = true)
public class WikiDocumentAccessImpl implements WikiDocumentAccess {
    private final AccessRepository repository;

    public WikiDocumentAccessImpl(AccessRepository repository) {
        this.repository = repository;
    }

    @Override
    public Set<String> available(String user, Collection<String> documentIds) {
        Optional<UUID> userId = parseId(user);
        if (userId.isEmpty()) {
            return Set.of();
        }
        Map<String, UUID> parsed = documentIds.stream()
                .distinct()
                .flatMap(id -> parseId(id).stream().map(uuid -> Map.entry(id, uuid)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (parsed.isEmpty()) {
            return Set.of();
        }
        Set<UUID> available = repository.findAvailableDocuments(userId.get(), parsed.values());
        return parsed.keySet().stream()
                .filter(id -> available.contains(parsed.get(id)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<UUID> parseId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(id);
            // UUID.fromString also accepts abbreviated groups, which are not UUID document ids.
            return uuid.toString().equalsIgnoreCase(id) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
