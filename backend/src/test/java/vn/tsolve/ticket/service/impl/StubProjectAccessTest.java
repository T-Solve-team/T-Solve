package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StubProjectAccessTest {

    @Test
    void managesOnlyTheProjectsListedForTheUser() {
        StubProjectAccess access = new StubProjectAccess("alice:1,2; bob : 3", "");

        assertThat(access.managesActive("alice", 1)).isTrue();
        assertThat(access.managesActive("alice", 2)).isTrue();
        assertThat(access.managesActive("alice", 3)).isFalse();
        assertThat(access.managesActive("bob", 3)).isTrue();
    }

    @Test
    void failsClosedWhenNothingIsConfigured() {
        StubProjectAccess access = new StubProjectAccess("", "");

        assertThat(access.managesActive("alice", 1)).isFalse();
    }

    @Test
    void failsClosedForAUserWhoIsNotListed() {
        StubProjectAccess access = new StubProjectAccess("alice:1", "");

        assertThat(access.managesActive("mallory", 1)).isFalse();
    }

    @Test
    void inactiveProjectIsNeitherActiveNorManaged() {
        StubProjectAccess access = new StubProjectAccess("alice:1,2", "2");

        assertThat(access.isActive(1)).isTrue();
        assertThat(access.isActive(2)).isFalse();
        assertThat(access.managesActive("alice", 2)).isFalse();
    }

    @Test
    void ignoresMalformedEntries() {
        StubProjectAccess access = new StubProjectAccess("alice:1,x;;:5;bob", "y");

        assertThat(access.managesActive("alice", 1)).isTrue();
        assertThat(access.managesActive("bob", 5)).isFalse();
        assertThat(access.isActive(1)).isTrue();
    }
}
