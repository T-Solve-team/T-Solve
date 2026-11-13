package vn.tsolve.identity.session;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.SignInProperties;

/**
 * The Platform Operator emails from {@code PLATFORM_OPERATOR_EMAILS}, read once at start-up (AC-001.29, .30): each item
 * trimmed, empty items dropped, compared whole and ignoring case.
 */
@Component
public class OperatorAllowList {

    private final Set<String> emails;

    @Autowired
    public OperatorAllowList(SignInProperties properties) {
        this(properties.operatorEmails());
    }

    public OperatorAllowList(String commaSeparated) {
        this.emails = Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean contains(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
