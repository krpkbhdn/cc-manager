package com.github.krpkbhdn.ccmanager.security;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "sk-ant-api03-abcdefghijklmnop1234",
            "sk-abcdefghijklmnopqrstuv",
            "ghp_abcdefghijklmnopqrstuvwx",
            "gho_abcdefghijklmnopqrstuvwx",
            "ghs_abcdefghijklmnopqrstuvwx",
            "ghu_abcdefghijklmnopqrstuvwx",
            "ghr_abcdefghijklmnopqrstuvwx",
            "xoxr-1234567890-abcdef",
            "xoxs-1234567890-abcdef",
            "ASIAABCDEFGHIJKLMNOP",
            "hf_abcdefghijklmnopqrstuvwxyzABCD",
            "SG.abcdefghijklmnop.qrstuvwxyzABCDEFG",
            "github_pat_abcdefghijklmnopqrstuv",
            "xoxb-1234567890-abcdef",
            "AKIAABCDEFGHIJKLMNOP",
            "AIzaSyA1234567890abcdefghijklmnopqrstuv",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIabc\n-----END RSA PRIVATE KEY-----",
            "0123456789abcdef0123456789abcdef01234567",
            "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU2Nzg5"})
    void masksTokenLikeFragments(String secret) {
        assertThat(SecretFilter.mask("before " + secret + " after")).isEqualTo("before [redacted] after");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password: hunter2", "secret=abc", "api_key = xyz", "api-key:k", "token=t"})
    void masksAssignments(String text) {
        assertThat(SecretFilter.mask(text)).contains("[redacted]").doesNotContain("hunter2", "abc", "xyz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"password\":\"hunter2\"}", "{\"apiKey\": \"hunter2\"}", "{\\\"token\\\":\\\"hunter2\\\"}",
            "login --password hunter2 now", "DB_PASS=hunter2", "APP_SECRET=hunter2", "GH_TOKEN=hunter2",
            "postgres://admin:hunter2@db:5432/app", "Authorization: Basic hunter2", "Authorization: Bearer hunter2",
            "{\"Authorization\": \"Bearer hunter2\"}"})
    void masksCredentialForms(String text) {
        assertThat(SecretFilter.mask(text)).contains("[redacted]").doesNotContain("hunter2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MYSQL_PWD=hunter2", "mysql -u root -phunter2 db", "sk_live_abcdefghijklmnop1234", "rk_live_abcdefghijklmnop1234",
            "glpat-abcdefghijklmnop1234", "npm_abcdefghijklmnopqrstuvwxyz0123456789", "Password: hunter2 horse battery"})
    void masksAdditionalCredentialForms(String text) {
        assertThat(SecretFilter.mask(text)).contains("[redacted]").doesNotContain("hunter2", "abcdefghijklmnop", "horse", "battery");
    }

    @Test
    void aLongWordRunIsProcessedQuicklyAndARealAssignmentStillMasked() {
        var run = "a1B2".repeat(25_000);

        var masked = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> SecretFilter.mask(run + " password=hunter2"));

        assertThat(masked).doesNotContain("hunter2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789abcdef0123456789ABCDEF", "abc-DEF_ghi123abc-DEF_ghi123abc-DEF_ghi123xyz",
            "aB3/dE5+fG7/hI9+jK1/lM3+nO5/pQ7+rS9/tU1+vW3"})
    void masksRandomBlobs(String blob) {
        assertThat(SecretFilter.mask("x " + blob + " y")).isEqualTo("x [redacted] y");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/com/github/krpkbhdn/ccmanager/claude/launch/ClaudeLauncher.java",
            "krpkbhdn/ccmanager/claude/launch/ClaudeLauncher",
            "Work/Code/Personal/cc-manager-session-search/src/main/java/ClaudeLauncher2",
            "com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher",
            "the bypass flag and basic configuration are fine"})
    void leavesPathsAndIdentifiersAlone(String text) {
        assertThat(SecretFilter.mask(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"just some plain text", "the token is explained below", "a/short/path/file.txt"})
    void leavesPlainTextAlone(String text) {
        assertThat(SecretFilter.mask(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cat .env", "{\"file_path\":\"/p/.env.local\"}", "C:\\p\\.env", "a/server.pem", "k.KEY", "x.p12", "x.pfx",
            "x.keystore", "~/.ssh/id_rsa", "~/.ssh/id_ed25519", "id_ecdsa", "id_dsa","id_rsa.pub", "~/.ssh/config", "C:\\Users\\a\\.aws\\config", "cat .netrc",
            ".npmrc", ".pypirc", "aws/credentials", "/p/secrets.yml", "cat ~/.pgpass", "~/.docker/config.json",
            "C:\\\\u\\\\.docker\\\\config.json", "prod.tfvars", "~/.kube/kubeconfig", "service-account-prod.json",
            ".git-credentials", "cat ~/.htpasswd", "client_secret_1.json", "terraform.tfstate", "terraform.tfstate.backup", "cat ~/.kube/config",
            "{\"command\":\"printenv\"}", "{\"command\":\"env\"}", "{\"command\":\"env | grep X\"}",
            "{\"command\":\"set\"}", "{\"command\":\"set; echo\"}"})
    void detectsSensitiveFiles(String input) {
        assertThat(SecretFilter.touchesSensitiveFile(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cat .environment", "src/Main.java", "the keyboard", "environment.txt", "ls -la", "{\"command\":\"env FOO=1 make\"}", "{\"command\":\"settings\"}"})
    void ignoresOrdinaryInput(String input) {
        assertThat(SecretFilter.touchesSensitiveFile(input)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/task-runner-configuration/Foo.java", "MSG.abcdefghijklmnop.qrstuvwxyzABCDEFG", "git show 0123456789abcdef0123456789abcdef01234567"})
    void commandTextKeepsLookalikesThatAreNoSecrets(String text) {
        assertThat(SecretFilter.maskCommandText(text)).isEqualTo(text);
    }

    @Test
    void commandTextKeepsKeyNamesButStillMasksSecretShapes() {
        assertThat(SecretFilter.maskCommandText("run --max-tokens 4096 max_tokens=4096")).startsWith("run --max-tokens 4096 max_tokens=");
        assertThat(SecretFilter.maskCommandText("x sk-abcdefghijklmnopqrstuv y")).isEqualTo("x [redacted] y");
        assertThat(SecretFilter.maskCommandText("a SG.abcdefghijklmnop.qrstuvwxyzABCDEFG")).isEqualTo("a [redacted]");
    }

    @Test
    void transcriptMasksLongUnbrokenLowercaseAndDigitTokens() {
        var lowercase = "k".repeat(20) + "q".repeat(25);
        var digits = "7".repeat(45);
        assertThat(SecretFilter.mask("out " + lowercase + " end")).isEqualTo("out [redacted] end");
        assertThat(SecretFilter.mask("out " + digits + " end")).isEqualTo("out [redacted] end");
        assertThat(SecretFilter.mask("branch " + "word-".repeat(10) + "word")).doesNotContain("[redacted]");
    }

    @Test
    void commandTextKeepsLongLowercaseTokens() {
        var text = "git show " + "a".repeat(40);
        assertThat(SecretFilter.maskCommandText(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization: token abc123def", "Authorization: ApiKey abc123def", "authorization=Basic abc123def", "Authorization: abc123def"})
    void anyAuthorizationSchemeIsMasked(String header) {
        assertThat(SecretFilter.maskCommandText(header)).doesNotContain("abc123def").endsWith("[redacted]");
    }

    @Test
    void commandTextMasksAPasswordPhraseOnlyUpToItsFirstWord() {
        assertThat(SecretFilter.maskCommandText("password: hunter2 # staging\n{\"a\":1}")).isEqualTo("password: [redacted] # staging\n{\"a\":1}");
        assertThat(SecretFilter.mask("password: hunter2 and more")).doesNotContain("and more");
    }

    @Test
    void commandTextMasksKeyValuesWhoseKeyContainsTokenSoMaxTokensValueIsHidden() {
        assertThat(SecretFilter.maskCommandText("max_tokens=4096")).isEqualTo("max_tokens=[redacted]");
        assertThat(SecretFilter.maskCommandText("--max-tokens 4096")).isEqualTo("--max-tokens 4096");
    }

    @Test
    void aKeyPrefixInsideAWordIsNotMasked() {
        assertThat(SecretFilter.mask("task-runner-configuration-abc")).isEqualTo("task-runner-configuration-abc");
    }
}
