package com.github.krpkbhdn.ccmanager.ui.git.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ForgeRemoteTest {
    @Test
    fun `github urls give owner and name`() {
        for (url in listOf("https://github.com/o/r.git", "git@github.com:o/r.git", "ssh://git@github.com/o/r", "https://github.com/o/r/")) {
            assertThat(forgeRemote(url)).isEqualTo(ForgeRemote(Forge.GITHUB, "github.com", "o/r"))
        }
    }

    @Test
    fun `other github hosts keep the host in the repository`() {
        assertThat(forgeRemote("https://github.evil.example/o/r")).isEqualTo(ForgeRemote(Forge.GITHUB, "github.evil.example", "github.evil.example/o/r"))
        assertThat(forgeRemote("git@github.acme.com:o/r.git")).isEqualTo(ForgeRemote(Forge.GITHUB, "github.acme.com", "github.acme.com/o/r"))
        assertThat(forgeRemote("https://GitHub.com/o/r")!!.repository).isEqualTo("o/r")
    }

    @Test
    fun `a github url needs exactly owner and name`() {
        assertThat(forgeRemote("https://github.com/owner")).isNull()
        assertThat(forgeRemote("https://github.com/a/b/c")).isNull()
    }

    @Test
    fun `gitlab urls give the full https address, subgroups included`() {
        assertThat(forgeRemote("https://gitlab.com/g/r.git")).isEqualTo(ForgeRemote(Forge.GITLAB, "gitlab.com", "https://gitlab.com/g/r"))
        assertThat(forgeRemote("git@gitlab.agroprosperis.com:team/sub/app.git"))
            .isEqualTo(ForgeRemote(Forge.GITLAB, "gitlab.agroprosperis.com", "https://gitlab.agroprosperis.com/team/sub/app"))
        assertThat(forgeRemote("ssh://git@gitlab.example.com:2222/a/b/c/d")).isEqualTo(ForgeRemote(Forge.GITLAB, "gitlab.example.com", "https://gitlab.example.com/a/b/c/d"))
        assertThat(forgeRemote("https://gitlab.com/onlyone")).isNull()
    }

    @Test
    fun `other hosts and non urls give nothing`() {
        assertThat(forgeRemote("git@example.com:acme/app.git")).isNull()
        assertThat(forgeRemote("/srv/origin.git")).isNull()
        assertThat(forgeRemote("not a url")).isNull()
        assertThat(forgeRemote(null)).isNull()
    }

    @Test
    fun `web urls keep their port and scheme, ssh drops the port`() {
        assertThat(forgeRemote("https://gitlab.example.com:8443/g/r.git"))
            .isEqualTo(ForgeRemote(Forge.GITLAB, "gitlab.example.com:8443", "https://gitlab.example.com:8443/g/r"))
        assertThat(forgeRemote("http://gitlab.local:8080/g/r")).isEqualTo(ForgeRemote(Forge.GITLAB, "gitlab.local:8080", "http://gitlab.local:8080/g/r"))
        assertThat(forgeRemote("http://gitlab.local/g/r")!!.repository).isEqualTo("http://gitlab.local/g/r")
    }

    @Test
    fun `edge urls`() {
        assertThat(forgeRemote("  https://gitlab.com/g/r.git\n")!!.repository).isEqualTo("https://gitlab.com/g/r")
        assertThat(forgeRemote("https://user:tok@gitlab.com/g/s/r.git")!!.repository).isEqualTo("https://gitlab.com/g/s/r")
        assertThat(forgeRemote("git@GitLab.Com:g/r.git")!!.forge).isEqualTo(Forge.GITLAB)
        assertThat(forgeRemote("https://gitlab.com/g//r")).isNull()
        assertThat(forgeRemote("https://gitlab.com/")).isNull()
        assertThat(forgeRemote("")).isNull()
        assertThat(forgeRemote("C:\\repos\\x")).isNull()
    }

    @Test
    fun `sign in hint names the host for gitlab`() {
        assertThat(forgeRemote("https://gitlab.com/g/r")!!.signInHint()).isEqualTo("Run `glab auth login --hostname gitlab.com` in a terminal")
        assertThat(forgeRemote("https://github.com/o/r")!!.signInHint()).isEqualTo("Run `gh auth login` in a terminal")
    }
}
