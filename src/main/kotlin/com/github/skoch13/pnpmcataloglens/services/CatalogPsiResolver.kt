package com.github.skoch13.pnpmcataloglens.services

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Shared helpers to work with PNPM Catalog references and YAML PSI targets.
 * Centralizes detection of catalog refs and resolution of the corresponding YAML element.
 */
object CatalogPsiResolver {
    fun isCatalogRef(value: String): Boolean {
        if (!value.startsWith("catalog")) return false
        if (value == "catalog") return true
        if (!value.contains(":")) return false
        return true
    }

    /**
     * Resolves the YAML PSI element (prefer value scalar, fallback to key) for the given package and catalogRef.
     */
    fun resolveYamlTarget(project: Project, packageName: String, catalogRef: String): PsiElement? {
        val service = project.service<PnpmWorkspaceService>()
        val wsFile = service.findWorkspaceFile() ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(wsFile) as? YAMLFile ?: return null

        val doc = psiFile.documents.firstOrNull() ?: return null
        val top = doc.topLevelValue as? YAMLMapping ?: return null

        if (catalogRef == "catalog:") {
            val catalogMapping = top.getKeyValueByKey("catalog")?.value as? YAMLMapping ?: return null
            val kv = catalogMapping.getKeyValueByKey(packageName) ?: return null
            return kv.value ?: kv.key
        }

        if (catalogRef.startsWith("catalog:")) {
            val name = catalogRef.removePrefix("catalog:")
            val catalogsMapping = top.getKeyValueByKey("catalogs")?.value as? YAMLMapping ?: return null
            val namedCatalog = catalogsMapping.getKeyValueByKey(name)?.value as? YAMLMapping ?: return null
            val kv = namedCatalog.getKeyValueByKey(packageName) ?: return null
            return kv.value ?: kv.key
        }

        return null
    }
}
