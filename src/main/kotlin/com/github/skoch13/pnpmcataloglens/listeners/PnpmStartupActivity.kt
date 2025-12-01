package com.github.skoch13.pnpmcataloglens.listeners

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project startup activity that triggers initial parsing and keeps PNPM catalog
 * data up-to-date by listening for changes to `pnpm-workspace.yaml`.
 */
class PnpmStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<PnpmWorkspaceService>()

        // Initial refresh on project open (no-op if no workspace file)
        if (service.hasPnpmWorkspace()) {
            service.refresh()
        }

        // Ensure the VFS watcher service is initialized so it owns the subscription lifecycle
        project.service<PnpmWorkspaceVfsWatcher>()
    }
}
