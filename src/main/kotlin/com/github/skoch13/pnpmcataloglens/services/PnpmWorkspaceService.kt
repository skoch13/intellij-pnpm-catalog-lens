package com.github.skoch13.pnpmcataloglens.services

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.yaml.snakeyaml.Yaml
import java.io.IOException

/**
 * Main service for the PNPM Catalog Lens plugin.
 * Handles pnpm-workspace.yaml files.
 * Detects if a project has a pnpm-workspace.yaml file and extracts catalog information from it.
 */
@Service(Service.Level.PROJECT)
class PnpmWorkspaceService(private val project: Project) {
    private val LOG = logger<PnpmWorkspaceService>()

    // Cache for a workspace file
    private var workspaceFile: VirtualFile? = null

    // Tracker we can bump explicitly (e.g., from VFS listener via refresh())
    private val catalogTracker = SimpleModificationTracker()

    // Cached catalog data (default + named) using IDE CachedValue invalidated by YAML PSI changes
    private val catalogData: CachedValue<CatalogData?> = CachedValuesManager.getManager(project).createCachedValue(
        {
            val wsFile = findWorkspaceFile() ?: return@createCachedValue CachedValueProvider.Result.create(null, catalogTracker)
            val psiFile = ReadAction.compute<com.intellij.psi.PsiFile?, Throwable> {
                PsiManager.getInstance(project).findFile(wsFile)
            }
            val content = ReadAction.compute<String, Throwable> {
                try {
                    // Prefer VfsUtilCore.loadText to respect charset
                    VfsUtilCore.loadText(wsFile)
                } catch (e: IOException) {
                    LOG.warn("Failed to read pnpm-workspace.yaml", e)
                    ""
                }
            }

            val parsed = parseYaml(content)
            // Invalidate on YAML PSI changes when available; always track our explicit catalogTracker
            if (psiFile != null) {
                CachedValueProvider.Result.create(
                    parsed,
                    psiFile,
                    catalogTracker
                )
            } else {
                CachedValueProvider.Result.create(
                    parsed,
                    catalogTracker
                )
            }
        }, false
    )

    private data class CatalogData(
        val defaultCatalog: Map<String, String>,
        val namedCatalogs: Map<String, Map<String, String>>
    )

    /**
     * Checks if the project has a pnpm-workspace.yaml file.
     */
    fun hasPnpmWorkspace(): Boolean {
        return findWorkspaceFile() != null
    }

    /**
     * Finds the pnpm-workspace.yaml file in the project.
     */
    fun findWorkspaceFile(): VirtualFile? {
        if (workspaceFile != null && workspaceFile!!.isValid) {
            return workspaceFile
        }

        val projectDir = project.guessProjectDir() ?: return null
        val workspaceFile = projectDir.findChild("pnpm-workspace.yaml")

        if (workspaceFile != null && workspaceFile.exists()) {
            this.workspaceFile = workspaceFile
            return workspaceFile
        }

        return null
    }

    /**
     * Parses the pnpm-workspace.yaml file and extracts catalog information.
     */
    fun parsePnpmWorkspace() {
        // Touch the cached value to ensure it computes once when first needed
        catalogData.value
    }

    private fun parseYaml(content: String): CatalogData? {
        if (content.isBlank()) return null
        return try {
            val yaml = Yaml()
            @Suppress("UNCHECKED_CAST")
            val data = yaml.load<Map<String, Any?>>(content) ?: emptyMap()

            val defaultCat = (data["catalog"] as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v as? String ?: return@mapNotNull null
                    key to value
                }?.toMap() ?: emptyMap()

            val namedCats = (data["catalogs"] as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (name, map) ->
                    val catalogName = name as? String ?: return@mapNotNull null
                    val values = (map as? Map<*, *>)?.entries
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v as? String ?: return@mapNotNull null
                            key to value
                        }?.toMap() ?: emptyMap()
                    catalogName to values
                }?.toMap() ?: emptyMap()

            LOG.info("Parsed pnpm-workspace.yaml: defaultCatalog=${defaultCat.size} entries, namedCatalogs=${namedCats.size} entries")
            CatalogData(defaultCat, namedCats)
        } catch (e: Exception) {
            LOG.warn("Failed to parse pnpm-workspace.yaml", e)
            null
        }
    }

    /**
     * Resolves a catalog version for a package.
     *
     * @param packageName The name of the package.
     * @param catalogRef The catalog reference (e.g. "catalog:" or "catalog:react18").
     * @return The resolved version or null if not found.
     */
    fun resolveCatalogVersion(packageName: String, catalogRef: String): String? {
        val data = catalogData.value ?: return null

        // Handle the default catalog shorthand "catalog:"
        if (catalogRef == "catalog:") {
            return data.defaultCatalog[packageName]
        }

        // Handle named catalogs "catalog:name"
        if (catalogRef.startsWith("catalog:")) {
            val catalogName = catalogRef.substring("catalog:".length)
            return data.namedCatalogs[catalogName]?.get(packageName)
        }

        return null
    }

    /**
     * Refreshes the catalog data by re-parsing the workspace file.
     */
    fun refresh() {
        // Force recomputation by bumping our explicit tracker
        catalogTracker.incModificationCount()
    }

    /**
     * Gets the default catalog.
     */
    fun getDefaultCatalog(): Map<String, String>? {
        return catalogData.value?.defaultCatalog
    }

    /**
     * Gets the named catalogs.
     */
    fun getNamedCatalogs(): Map<String, Map<String, String>>? {
        return catalogData.value?.namedCatalogs
    }

}
