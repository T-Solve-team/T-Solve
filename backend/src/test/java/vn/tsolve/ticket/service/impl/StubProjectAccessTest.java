package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StubProjectAccessTest {

    private static final String P1 = "00000000-0000-0000-0000-000000000001";
    private static final String P2 = "00000000-0000-0000-0000-000000000002";
    private static final String P3 = "00000000-0000-0000-0000-000000000003";

    @Test
    void managesOnlyTheProjectsListedForTheUser() {
        StubProjectAccess access = new StubProjectAccess("alice:" + P1 + "," + P2 + "; bob : " + P3, "");

        assertThat(access.managesActive("alice", id(P1))).isTrue();
        assertThat(access.managesActive("alice", id(P2))).isTrue();
        assertThat(access.managesActive("alice", id(P3))).isFalse();
        assertThat(access.managesActive("bob", id(P3))).isTrue();
    }

    @Test
    void failsClosedWhenNothingIsConfigured() {
        StubProjectAccess access = new StubProjectAccess("", "");

        assertThat(access.managesActive("alice", id(P1))).isFalse();
    }

    @Test
    void failsClosedForAUserWhoIsNotListed() {
        StubProjectAccess access = new StubProjectAccess("alice:" + P1, "");

        assertThat(access.managesActive("mallory", id(P1))).isFalse();
    }

    @Test
    void inactiveProjectIsNeitherActiveNorManaged() {
        StubProjectAccess access = new StubProjectAccess("alice:" + P1 + "," + P2, P2);

        assertThat(access.isActive(id(P1))).isTrue();
        assertThat(access.isActive(id(P2))).isFalse();
        assertThat(access.managesActive("alice", id(P2))).isFalse();
    }

    @Test
    void ignoresMalformedEntries() {
        StubProjectAccess access = new StubProjectAccess("alice:" + P1 + ",x;;:" + P3 + ";bob", "y");

        assertThat(access.managesActive("alice", id(P1))).isTrue();
        assertThat(access.managesActive("bob", id(P3))).isFalse();
        assertThat(access.isActive(id(P1))).isTrue();
    }

    @Test
    void aShortFormIdGrantsNothing_BUG_080() {
        StubProjectAccess access = new StubProjectAccess("alice:1-1-1-1-1", "");

        assertThat(access.managesActive("alice", id("00000001-0001-0001-0001-000000000001")))
                .isFalse();
    }

    @Test
    void anOldNumericIdGrantsNothing() {
        StubProjectAccess access = new StubProjectAccess("alice:1", "");

        assertThat(access.managesActive("alice", id(P1))).isFalse();
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
