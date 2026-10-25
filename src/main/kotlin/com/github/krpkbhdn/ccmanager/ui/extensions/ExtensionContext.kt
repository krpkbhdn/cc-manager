package com.github.krpkbhdn.ccmanager.ui.extensions

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import java.nio.file.Path

/** Where an extensions page works: for the user everywhere, or for one project. Every extension page takes one. */
sealed interface ExtensionContext {
    val projectPath: Path?

    /** Scopes an extension can be installed or changed in from here. */
    val writableScopes: List<ExtensionScope>

    data object Global : ExtensionContext {
        override val projectPath: Path? = null
        override val writableScopes = listOf(ExtensionScope.USER)
    }

    data class ForProject(val id: Long, val name: String, override val projectPath: Path) : ExtensionContext {
        override val writableScopes = listOf(ExtensionScope.PROJECT, ExtensionScope.LOCAL)
    }

    /** True when something stored in [scope] (for [path] if project or local) can be changed from this context. */
    fun owns(scope: ExtensionScope, path: Path?): Boolean = scope in writableScopes && (scope == ExtensionScope.USER || sameProject(path))

    /** True when it applies here: owned, or inherited from the user scope. */
    fun sees(scope: ExtensionScope, path: Path?): Boolean = scope == ExtensionScope.USER || owns(scope, path)

    private fun sameProject(path: Path?): Boolean = path != null && projectPath?.toAbsolutePath()?.normalize() == path.toAbsolutePath().normalize()
}
