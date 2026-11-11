package vn.tsolve.identity.account.service.impl;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.account.service.AccountLookupService;

@Service
public class AccountLookupServiceImpl implements AccountLookupService {

    private final AccountRepository accounts;

    public AccountLookupServiceImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TSolveUser> findActive(UUID accountId) {
        return accounts.findById(accountId)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .map(account -> new TSolveUser(
                        account.getId(),
                        account.getEmail(),
                        account.getFullName() == null ? "" : account.getFullName(),
                        Set.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findName(UUID accountId) {
        return accounts.findById(accountId).map(Account::getFullName);
    }
}
