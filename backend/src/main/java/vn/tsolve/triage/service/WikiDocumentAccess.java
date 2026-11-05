package vn.tsolve.triage.service;

import java.util.Collection;
import java.util.Set;

/** Which of these Wiki document ids the user may open now; the wiki module owns documents (AC-004.3). */
public interface WikiDocumentAccess {

    Set<String> available(String user, Collection<String> documentIds);
}
