package com.github.skoch13.pnpmcataloglens.references

import com.github.skoch13.pnpmcataloglens.services.CatalogPsiResolver
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext

/**
 * Contributes references from package.json catalog entries (values like "catalog:" or "catalog:name")
 * to the corresponding entry in pnpm-workspace.yaml so users can Cmd/Ctrl-click to navigate.
 */
class PnpmCatalogReferenceContributor : PsiReferenceContributor(), DumbAware {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(JsonStringLiteral::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val jsonLiteral = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY

                    // Restrict to package.json files only
                    val vf = jsonLiteral.containingFile?.virtualFile
                    if (vf == null || vf.name != "package.json") return PsiReference.EMPTY_ARRAY

                    // Case 1: Reference from the VALUE (e.g., "catalog:" or "catalog:name")
                    val value = jsonLiteral.value
                    if (CatalogPsiResolver.isCatalogRef(value)) {
                        val packageName = jsonLiteral.parent?.firstChild?.text?.trim('"')
                        if (packageName != null) {
                            return arrayOf(PnpmCatalogYamlReference(jsonLiteral, packageName, value))
                        }
                    }

                    return PsiReference.EMPTY_ARRAY
                }
            },
            HIGHER_PRIORITY
        )
    }

}

private class PnpmCatalogYamlReference(
    element: JsonStringLiteral,
    private val packageName: String,
    private val catalogRef: String
) : PsiReferenceBase<JsonStringLiteral>(element, true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        return CatalogPsiResolver.resolveYamlTarget(project, packageName, catalogRef)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
