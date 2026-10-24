package com.github.krpkbhdn.ccmanager.ui.git

import com.github.krpkbhdn.ccmanager.git.GitLog
import java.time.Instant

/** A commit while the sample history is being scripted; [id] doubles as its hash. */
class Scripted(val id: String, val parents: List<Scripted>, val subject: String, val author: String, val minutesAgo: Long)

/**
 * Builds a realistic multi-lane history for fakes: script commits, then [rows] lays them out newest first with lanes,
 * colour slots and links to the next row, the way the real log service does.
 */
class SampleHistory(private val now: Instant = Instant.now()) {
    private val scripted = mutableListOf<Scripted>()
    private val refs = mutableMapOf<String, MutableList<GitLog.Ref>>()

    fun commit(subject: String, author: String, minutesAgo: Long, vararg parents: Scripted): Scripted =
        Scripted("%08x".format((subject + scripted.size).hashCode()).repeat(HASH_REPEAT), parents.toList(), subject, author, minutesAgo).also { scripted += it }

    fun ref(commit: Scripted, name: String, kind: GitLog.RefKind) {
        refs.getOrPut(commit.id) { mutableListOf() }.add(GitLog.Ref(name, kind))
    }

    fun rows(): List<GitLog.Row> {
        val newestFirst = scripted.sortedBy { it.minutesAgo }
        val layout = LaneLayout(newestFirst)
        return newestFirst.mapIndexed { index, commit ->
            GitLog.Row(toCommit(commit), layout.laneOf(index), layout.laneOf(index) % COLORS, layout.widthAt(index), layout.linksAt(index))
        }
    }

    private fun toCommit(commit: Scripted) = GitLog.Commit(
        commit.id, commit.id.take(SHORT), commit.parents.map { it.id }, commit.author, commit.author.lowercase().replace(' ', '.') + "@acme.dev",
        now.minusSeconds(commit.minutesAgo * SECONDS_PER_MINUTE), commit.subject, "", refs[commit.id].orEmpty(),
    )

    private companion object {
        const val SHORT = 7
        const val COLORS = 8
        const val SECONDS_PER_MINUTE = 60L
        const val HASH_REPEAT = 5
    }
}

/**
 * Assigns lanes top-down. A lane holds the hash its line is waiting for; a commit takes the leftmost lane waiting for it,
 * continues into its first parent's lane and opens or joins lanes for the other parents.
 */
private class LaneLayout(private val commits: List<Scripted>) {
    private val lanes = IntArray(commits.size)
    private val before = mutableListOf<List<String?>>()
    private val after = mutableListOf<List<String?>>()
    private val dotTargets = mutableListOf<List<Int>>()

    init {
        val active = mutableListOf<String?>()
        commits.forEachIndexed { index, commit ->
            before += active.toList()
            val waiting = active.indices.filter { active[it] == commit.id }
            val lane = waiting.firstOrNull() ?: firstFree(active).also { place(active, it, commit.id) }
            waiting.drop(1).forEach { active[it] = null }
            lanes[index] = lane
            active[lane] = null
            val targets = mutableListOf<Int>()
            commit.parents.forEachIndexed { number, parent ->
                val existing = active.indexOf(parent.id)
                when {
                    existing >= 0 -> targets += existing
                    number == 0 -> { active[lane] = parent.id; targets += lane }
                    else -> firstFree(active).let { place(active, it, parent.id); targets += it }
                }
            }
            after += active.toList()
            dotTargets += targets
        }
    }

    fun laneOf(index: Int) = lanes[index]

    fun widthAt(index: Int): Int =
        (listOf(lanes[index]) + dotTargets[index] + usedLanes(after[index]) + usedLanes(before[index])).max() + 1

    fun linksAt(index: Int): List<GitLog.GraphLink> {
        val next = commits.getOrNull(index + 1)
        val lane = lanes[index]
        fun target(at: Int) = if (next != null && after[index][at] == next.id) lanes[index + 1] else at
        val fromDot = dotTargets[index].map { t -> GitLog.GraphLink(lane, target(t), t % COLORS) }
        val passing = usedLanes(after[index]).filter { it !in dotTargets[index] }.map { GitLog.GraphLink(it, target(it), it % COLORS) }
        return fromDot + passing
    }

    private fun usedLanes(state: List<String?>) = state.indices.filter { state[it] != null }

    private fun firstFree(active: MutableList<String?>): Int = active.indexOfFirst { it == null }.takeIf { it >= 0 } ?: active.size

    private fun place(active: MutableList<String?>, at: Int, id: String) {
        while (active.size <= at) active += null
        active[at] = id
    }

    private companion object {
        const val COLORS = 8
    }
}
