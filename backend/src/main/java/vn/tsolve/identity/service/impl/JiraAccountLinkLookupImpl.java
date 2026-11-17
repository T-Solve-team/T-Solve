package vn.tsolve.identity.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.identity.dto.LinkedAccount;
import vn.tsolve.identity.jiralink.JiraAccountLinkRepository;
import vn.tsolve.identity.service.JiraAccountLinkLookup;

@Service
public class JiraAccountLinkLookupImpl implements JiraAccountLinkLookup {

    private final JiraAccountLinkRepository links;
    private final AccountLookupService accounts;

    public JiraAccountLinkLookupImpl(JiraAccountLinkRepository links, AccountLookupService accounts) {
        this.links = links;
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LinkedAccount> find(String trackerAccountId) {
        if (trackerAccountId == null || trackerAccountId.isBlank()) {
            return Optional.empty();
        }
        return links.find(trackerAccountId).flatMap(link -> accounts.findActive(link.accountId())
                .map(user -> new LinkedAccount(user, link.linkedAt())));
    }
}
