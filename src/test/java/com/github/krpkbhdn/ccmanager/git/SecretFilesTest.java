package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretFilesTest {

    @ParameterizedTest
    @ValueSource(strings = {".env", ".env.local", "config/.env.production", "prod.env", ".npmrc", "app/.netrc", ".pgpass", "infra/prod.tfvars",
            "release.jks", "putty.ppk", "cert.p12", "cert.PFX", "service-account-prod.json", "service-account.json", "secrets.yaml", "config\\secrets.json",
            "id_ed25519", "home/.ssh/id_rsa", "vault.kdbx", "credentials", "credentials.json", "server.pem", "server.key", "app.keystore",
            ".git-credentials", "home/.git-credentials", "client_secret.json", "client_secret_123.apps.googleusercontent.com.json", "terraform.tfstate",
            "infra/terraform.tfstate.backup", ".htpasswd", "web/.htpasswd", ".kube/config", "home\\.kube\\config"})
    void recognisesSecretNames(String path) {
        assertThat(SecretFiles.looksSecret(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"README.md", "src/Main.java", "environment.md", "id_rsa.pub", "keystore.md", "my.env.md", "secrets-howto/readme.md", "npmrc.txt", "service.json",
            "client_secret.md", "tfstate.md", "htpasswd.md", "kube/config", "docs/.kube/config.md"})
    void ignoresOrdinaryNames(String path) {
        assertThat(SecretFiles.looksSecret(path)).isFalse();
    }
}
