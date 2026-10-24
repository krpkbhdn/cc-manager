package com.github.krpkbhdn.ccmanager.ui.git

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit

private const val GIT_TIMEOUT_SECONDS = 30L

/**
 * Builds a throwaway repository with a local bare "origin" next to it, for real-mode previews: history with a merge, a
 * remote branch, a tag, a stash, one commit the clone does not have yet, and staged / unstaged / untracked changes.
 */
object GitDemoRepo {
    fun create(sandbox: Path): Path {
        val origin = sandbox.resolve("git-demo-origin.git")
        val work = sandbox.resolve("git-demo")
        val peer = sandbox.resolve("git-demo-peer")
        listOf(origin, work, peer).forEach(::deleteRecursively)
        Files.createDirectories(sandbox)
        git(sandbox, "init", "--bare", "-b", "main", origin.toString())
        git(sandbox, "clone", origin.toString(), work.toString())
        configure(work)
        commit(work, "README.md", "# git demo\n", "Initial commit")
        commit(work, "src/App.java", "class App {\n    void run() {}\n}\n", "Add App")
        git(work, "push", "-u", "origin", "main")
        git(work, "checkout", "-b", "feature/login")
        commit(work, "src/Login.java", "class Login {}\n", "Add login form")
        commit(work, "src/Login.java", "class Login {\n    boolean ok() { return true; }\n}\n", "Validate credentials")
        git(work, "push", "-u", "origin", "feature/login")
        git(work, "checkout", "main")
        commit(work, "docs/notes.md", "notes\n", "Docs: notes")
        git(work, "merge", "--no-ff", "-m", "Merge branch 'feature/login'", "feature/login")
        git(work, "tag", "-a", "v0.1.0", "-m", "First demo release")
        git(work, "push", "--follow-tags", "origin", "main")
        git(work, "checkout", "-b", "feature/draft")
        commit(work, "src/Draft.java", "class Draft {}\n", "Start draft")
        git(work, "checkout", "main")
        pushFromPeer(origin, peer)
        Files.writeString(work.resolve("README.md"), "# git demo\n\nStash me.\n")
        git(work, "stash", "push", "-m", "parked readme edit")
        Files.writeString(work.resolve("src/App.java"), "class App {\n    void run() {\n        System.out.println(\"hi\");\n    }\n}\n")
        Files.writeString(work.resolve("src/Login.java"), "class Login {\n    boolean ok() { return false; }\n}\n")
        git(work, "add", "src/Login.java")
        Files.writeString(work.resolve("src/Scratch.java"), "class Scratch {}\n")
        return work
    }

    private fun pushFromPeer(origin: Path, peer: Path) {
        git(peer.parent, "clone", origin.toString(), peer.toString())
        configure(peer)
        commit(peer, "docs/notes.md", "notes\nmore notes from a teammate\n", "Docs: more notes")
        git(peer, "push", "origin", "main")
        deleteRecursively(peer)
    }

    private fun configure(repo: Path) {
        git(repo, "config", "user.name", "Demo User")
        git(repo, "config", "user.email", "demo@example.com")
        git(repo, "config", "commit.gpgsign", "false")
    }

    private fun commit(repo: Path, file: String, content: String, message: String) {
        val target = repo.resolve(file)
        target.parent?.let(Files::createDirectories)
        Files.writeString(target, content)
        git(repo, "add", file)
        git(repo, "commit", "-m", message)
    }

    fun git(directory: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
        return output
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { it.toFile().setWritable(true); Files.delete(it) } }
    }
}
