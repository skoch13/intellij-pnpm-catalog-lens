package com.github.skoch13.pnpmcataloglens.folding

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Folding builder for PNPM catalog references in package.json files.
 * Creates folds that show the resolved versions for catalog references.
 * Implements DumbAware to function during indexing.
 */
class PnpmCatalogFoldingBuilder : FoldingBuilderEx(), DumbAware {
    companion object {
        private const val CATALOG_PREFIX = "catalog:"
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        // Only process JSON files with JsonLanguage
        if (root.language != JsonLanguage.INSTANCE || root !is JsonFile) {
            return FoldingDescriptor.EMPTY_ARRAY
        }

        val descriptors = mutableListOf<FoldingDescriptor>()
        val project = root.project
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // If the project doesn't have a pnpm-workspace.yaml file, don't do anything
        if (!pnpmWorkspaceService.hasPnpmWorkspace()) {
            return FoldingDescriptor.EMPTY_ARRAY
        }

        val workspaceFile = pnpmWorkspaceService.findWorkspaceFile()
        val workspaceYamlPsiFile = workspaceFile?.let { PsiManager.getInstance(project).findFile(it) }

        // Process all string literals in the file
        PsiTreeUtil.processElements(root) { element ->
            if (element is JsonStringLiteral && isValidCatalogReference(element.value)) {
                val packageName = element.parent?.firstChild?.text?.trim('"')
                if (packageName != null) {
                    val catalogVersion = pnpmWorkspaceService.resolveCatalogVersion(packageName, element.value)
                    if (catalogVersion != null) {
                        // Create dependencies for proper invalidation
                        val dependencies = when {
                            workspaceYamlPsiFile != null -> setOf<Any>(element, element.containingFile, workspaceYamlPsiFile)
                            workspaceFile != null && workspaceFile.isValid -> setOf<Any>(element, element.containingFile, workspaceFile)
                            else -> setOf<Any>(element, element.containingFile)
                        }

                        // Create a folding descriptor for this catalog reference
                        descriptors.add(
                            FoldingDescriptor(
                                element.node,
                                TextRange(
                                    element.textRange.startOffset,
                                    element.textRange.endOffset
                                ),
                                null,
                                dependencies,
                                true
                            )
                        )
                    }
                }
            }
            true
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val element = node.psi as? JsonStringLiteral ?: return "..."
        val value = element.value
        if (!isValidCatalogReference(value)) return "..."

        val packageName = element.parent?.firstChild?.text?.trim('"') ?: return "..."
        val project = element.project
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        val catalogVersion = pnpmWorkspaceService.resolveCatalogVersion(packageName, value)
        return "\"${catalogVersion ?: "..."}\""
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true

    private fun isValidCatalogReference(value: String): Boolean {
        if (!value.startsWith("catalog")) return false
        if (value == "catalog") return true
        if (!value.contains(":")) return false
        if (value == CATALOG_PREFIX) return true
        return value.length > CATALOG_PREFIX.length
    }
}
