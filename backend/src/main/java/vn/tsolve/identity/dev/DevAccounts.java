package vn.tsolve.identity.dev;

import java.util.List;
import java.util.UUID;

/** The dev accounts and the two dev Workspaces' constants; Task 23's realm import and tests read the same values. */
public final class DevAccounts {

    public static final UUID ACME_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final String ACME_SLUG = "acme";
    public static final String ACME_ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000c1";

    public static final UUID BETA_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    public static final String BETA_SLUG = "beta";
    public static final String BETA_ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000c2";

    private DevAccounts() {}

    public record DevAccount(String email, UUID workspaceId, String displayName) {}

    public static List<DevAccount> all() {
        return List.of(
                new DevAccount("alice@acme.test", ACME_ID, "Alice Acme"),
                new DevAccount("bob@beta.test", BETA_ID, "Bob Beta"));
    }
}
