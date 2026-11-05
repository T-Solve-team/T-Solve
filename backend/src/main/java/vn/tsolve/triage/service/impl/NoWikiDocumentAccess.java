package vn.tsolve.triage.service.impl;

import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.tsolve.triage.service.WikiDocumentAccess;

/** Stand-in until the wiki module exists. Fails closed: no document is available, so none is linked (AC-004.3). */
@Component
public class NoWikiDocumentAccess implements WikiDocumentAccess {

    @Override
    public Set<String> available(String user, Collection<String> documentIds) {
        return Set.of();
    }
}
