package vn.tsolve.triage.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Component;
import vn.tsolve.triage.service.JiraSite;

/** Stand-in until the workspace table has {@code jira_site_url}: no site, so no issue link is built. */
@Component
public class NoJiraSite implements JiraSite {

    @Override
    public Optional<String> url() {
        return Optional.empty();
    }
}
