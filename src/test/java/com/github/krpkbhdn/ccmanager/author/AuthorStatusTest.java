package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthorStatusTest {

    static Optional<AuthorDecision> decision(AuthorChoice choice, int version) {
        return Optional.of(new AuthorDecision(AuthorComponent.USER_SETUP, 0, choice, version));
    }

    @Test
    void noDecisionIsNotInstalledAndOffered() {
        var status = AuthorStatus.of(Optional.empty(), 1);

        assertThat(status).isEqualTo(AuthorStatus.NOT_INSTALLED);
        assertThat(status.offered()).isTrue();
    }

    @Test
    void installedCurrentVersionIsNotOffered() {
        var status = AuthorStatus.of(decision(AuthorChoice.INSTALLED, 2), 2);

        assertThat(status).isEqualTo(AuthorStatus.INSTALLED);
        assertThat(status.offered()).isFalse();
    }

    @Test
    void installedOlderVersionOffersAnUpdate() {
        var status = AuthorStatus.of(decision(AuthorChoice.INSTALLED, 1), 2);

        assertThat(status).isEqualTo(AuthorStatus.UPDATE_AVAILABLE);
        assertThat(status.offered()).isTrue();
    }

    @Test
    void declinedIsNeverOfferedEvenWhenTheBundleIsNewer() {
        var status = AuthorStatus.of(decision(AuthorChoice.DECLINED, 1), 5);

        assertThat(status).isEqualTo(AuthorStatus.DECLINED);
        assertThat(status.offered()).isFalse();
    }
}
