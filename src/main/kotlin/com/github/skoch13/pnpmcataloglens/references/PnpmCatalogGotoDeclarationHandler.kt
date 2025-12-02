package com.github.skoch13.pnpmcataloglens.references

import com.github.skoch13.pnpmcataloglens.services.CatalogPsiResolver
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Enables Go To Declaration from the dependency VALUE string (e.g., "catalog:" or "catalog:name")
 * even when the value is folded. Clicking Cmd/Ctrl on the folded placeholder places the caret at
 * the start of the string literal; this handler still resolves and navigates based on the underlying
 * JSON string PSI at that offset.
 */
class PnpmCatalogGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(element: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (element == null) return null

        val file = element.containingFile as? JsonFile ?: return null
        val vFile = file.virtualFile ?: return null
        if (vFile.name != "package.json") return null

        val project: Project = element.project

        // Find the JsonStringLiteral that contains the offset, or its parent at the start offset
        val atOffset = file.findElementAt(offset)
        val str = PsiTreeUtil.getParentOfType(atOffset, JsonStringLiteral::class.java, /* strict = */ false)
            ?: run {
                // If the caret is at the very start of the folded region, atOffset might be null
                // or outside the literal's body; try to use the passed-in element's parent chain.
                PsiTreeUtil.getParentOfType(element, JsonStringLiteral::class.java, false)
            }
            ?: return null

        val value = str.value
        if (!isCatalogRef(value)) return null

        // Package name is the key name of the surrounding property: the first child sibling of the value literal
        val packageName = str.parent?.firstChild?.text?.trim('"') ?: return null

        val target = CatalogPsiResolver.resolveYamlTarget(project, packageName, value) ?: return null
        return arrayOf(target)
    }

    override fun getActionText(context: DataContext): String? = null

    private fun isCatalogRef(value: String): Boolean = CatalogPsiResolver.isCatalogRef(value)
}
