package com.github.skoch13.pnpmcataloglens.references

import com.github.skoch13.pnpmcataloglens.services.CatalogPsiResolver
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement

/**
 * Adds a gutter icon next to package.json dependencies that are provided via PNPM Catalogs
 * and navigates to the corresponding entry in pnpm-workspace.yaml even when the value is folded.
 */
class PnpmCatalogLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        // Only in package.json files
        val vFile = element.containingFile?.virtualFile ?: return
        if (vFile.name != "package.json") return

        when (element) {
            is JsonStringLiteral -> {
                // Contribute exactly one gutter marker per dependency property:
                // only when the element is the KEY (property name). We intentionally
                // skip contributing on the VALUE to avoid duplicate icons/actions.
                val property = element.parent as? JsonProperty ?: return
                if (property.nameElement != element) return

                val valueElement = property.value as? JsonStringLiteral ?: return
                val catalogRef = valueElement.value
                if (!CatalogPsiResolver.isCatalogRef(catalogRef)) return

                val packageName = element.value
                addMarkerIfResolvable(element, packageName, catalogRef, result)
            }
        }
    }

    private fun addMarkerIfResolvable(
        anchor: PsiElement,
        packageName: String,
        catalogRef: String,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        val target = CatalogPsiResolver.resolveYamlTarget(anchor.project, packageName, catalogRef) ?: return
        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(target)
            .setTooltipText("Go to catalog definition in pnpm-workspace.yaml")
        result.add(builder.createLineMarkerInfo(anchor))
    }
}
