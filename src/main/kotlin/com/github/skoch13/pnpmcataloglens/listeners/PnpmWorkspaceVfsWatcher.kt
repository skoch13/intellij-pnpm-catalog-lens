package com.github.skoch13.pnpmcataloglens.listeners

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Project-level service that owns the VFS subscription for watching
 * changes to `pnpm-workspace.yaml`. This avoids using Project as a Disposable.
 */
@Service(Service.Level.PROJECT)
class PnpmWorkspaceVfsWatcher(private val project: Project) : Disposable {

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val fileIndex = ProjectFileIndex.getInstance(project)
                    if (
                        events.any { e ->
                            val f = e.file
                            f != null && f.name == "pnpm-workspace.yaml" && fileIndex.isInContent(f)
                        }
                    ) {
                        project.service<PnpmWorkspaceService>().refresh()
                    }
                }
            }
        )
    }

    override fun dispose() {
        // connection is tied to this service; nothing else to do
    }
}
