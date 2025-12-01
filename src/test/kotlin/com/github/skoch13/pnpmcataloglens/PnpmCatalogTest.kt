package com.github.skoch13.pnpmcataloglens

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.github.skoch13.pnpmcataloglens.folding.PnpmCatalogFoldingBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for the PNPM catalog lens functionality.
 */
class PnpmCatalogTest : BasePlatformTestCase() {
    private lateinit var workspaceFile: VirtualFile
    private lateinit var packageJsonFile: VirtualFile
    private lateinit var tempDir: File

    public override fun setUp() {
        super.setUp()

        // Create a temporary directory for the test
        tempDir = createTempDirectory().toFile()

        // Create a pnpm-workspace.yaml file
        val workspaceYaml = """
            packages:
              - packages/*

            catalog:
              react: ^18.3.1
              redux: ^5.0.1

            catalogs:
              react17:
                react: ^17.0.2
                react-dom: ^17.0.2

              react18:
                react: ^18.2.0
                react-dom: ^18.2.0
        """.trimIndent()

        val workspaceYamlFile = File(tempDir, "pnpm-workspace.yaml")
        workspaceYamlFile.writeText(workspaceYaml)

        // Create a package.json file with catalog references
        val packageJson = """
            {
              "name": "@example/app",
              "dependencies": {
                "react": "catalog:",
                "redux": "catalog:",
                "react-dom": "catalog:react17"
              }
            }
        """.trimIndent()

        val packagesDir = File(tempDir, "packages")
        packagesDir.mkdirs()

        val exampleAppDir = File(packagesDir, "example-app")
        exampleAppDir.mkdirs()

        val packageJsonFile = File(exampleAppDir, "package.json")
        packageJsonFile.writeText(packageJson)

        // Refresh the virtual file system to see the new files
        LocalFileSystem.getInstance().refresh(false)

        // Get the virtual files
        workspaceFile = LocalFileSystem.getInstance().findFileByIoFile(workspaceYamlFile)!!
        this.packageJsonFile = LocalFileSystem.getInstance().findFileByIoFile(packageJsonFile)!!
    }

    public override fun tearDown() {
        // Clean up temporary files
        tempDir.deleteRecursively()
        super.tearDown()
    }

    /**
     * Tests that the PnpmWorkspaceService correctly parses the pnpm-workspace.yaml file.
     */
    fun testPnpmWorkspaceService() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Set the workspace file manually for testing
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Ensure any previous cached value computed in other tests is invalidated
        pnpmWorkspaceService.refresh()

        // Parse the workspace file
        pnpmWorkspaceService.parsePnpmWorkspace()

        // Check the default catalog
        val defaultCatalog = pnpmWorkspaceService.getDefaultCatalog()
        assertNotNull("Default catalog should not be null", defaultCatalog)
        assertEquals("Default catalog should have 2 entries", 2, defaultCatalog!!.size)
        assertEquals("React version should be ^18.3.1", "^18.3.1", defaultCatalog["react"])
        assertEquals("Redux version should be ^5.0.1", "^5.0.1", defaultCatalog["redux"])

        // Check the named catalogs
        val namedCatalogs = pnpmWorkspaceService.getNamedCatalogs()
        assertNotNull("Named catalogs should not be null", namedCatalogs)
        assertEquals("There should be 2 named catalogs", 2, namedCatalogs!!.size)

        // Check the react17 catalog
        val react17Catalog = namedCatalogs["react17"]
        assertNotNull("react17 catalog should not be null", react17Catalog)
        assertEquals("react17 catalog should have 2 entries", 2, react17Catalog!!.size)
        assertEquals("React version in react17 catalog should be ^17.0.2", "^17.0.2", react17Catalog["react"])
        assertEquals("React DOM version in react17 catalog should be ^17.0.2", "^17.0.2", react17Catalog["react-dom"])

        // Check the react18 catalog
        val react18Catalog = namedCatalogs["react18"]
        assertNotNull("react18 catalog should not be null", react18Catalog)
        assertEquals("react18 catalog should have 2 entries", 2, react18Catalog!!.size)
        assertEquals("React version in react18 catalog should be ^18.2.0", "^18.2.0", react18Catalog["react"])
        assertEquals("React DOM version in react18 catalog should be ^18.2.0", "^18.2.0", react18Catalog["react-dom"])
    }

    /**
     * Tests that the PnpmWorkspaceService correctly resolves catalog references.
     */
    fun testResolveCatalogReferences() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Set the workspace file manually for testing
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Ensure any previous cached value computed in other tests is invalidated
        pnpmWorkspaceService.refresh()

        // Parse the workspace file
        pnpmWorkspaceService.parsePnpmWorkspace()

        // Test resolving catalog references
        assertEquals("Should resolve react from default catalog", "^18.3.1", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))
        assertEquals("Should resolve redux from default catalog", "^5.0.1", pnpmWorkspaceService.resolveCatalogVersion("redux", "catalog:"))
        assertEquals("Should resolve react from react17 catalog", "^17.0.2", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:react17"))
        assertEquals("Should resolve react-dom from react17 catalog", "^17.0.2", pnpmWorkspaceService.resolveCatalogVersion("react-dom", "catalog:react17"))
        assertEquals("Should resolve react from react18 catalog", "^18.2.0", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:react18"))
        assertEquals("Should resolve react-dom from react18 catalog", "^18.2.0", pnpmWorkspaceService.resolveCatalogVersion("react-dom", "catalog:react18"))

        // Test resolving non-existent catalog references
        assertNull("Should return null for non-existent package", pnpmWorkspaceService.resolveCatalogVersion("non-existent", "catalog:"))
        assertNull("Should return null for non-existent catalog", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:non-existent"))
    }

    /**
     * Tests folding builder placeholder texts for catalog references.
     */
    fun testFoldingBuilderPlaceholders() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Point the service to our temp workspace file
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Ensure parsing is done once
        pnpmWorkspaceService.parsePnpmWorkspace()

        // Open package.json in the fixture project
        val psiFile = myFixture.configureByText(
            "package.json",
            // match the content used above so service can resolve versions
            """
            {
              "name": "@example/app",
              "dependencies": {
                "react": "catalog:",
                "redux": "catalog:",
                "react-dom": "catalog:react17"
              }
            }
            """.trimIndent()
        )

        val builder = PnpmCatalogFoldingBuilder()
        val stringLiterals = PsiTreeUtil.collectElementsOfType(psiFile, JsonStringLiteral::class.java)

        // Map package name -> placeholder text
        val placeholders = stringLiterals.associate { literal ->
            val packageName = literal.parent.firstChild.text.trim('"')
            packageName to builder.getPlaceholderText(literal.node)
        }

        assertEquals("\"^18.3.1\"", placeholders["react"])
        assertEquals("\"^5.0.1\"", placeholders["redux"])
        assertEquals("\"^17.0.2\"", placeholders["react-dom"])
    }

    /**
     * Tests that the service invalidates cached data when the workspace YAML changes.
     */
    fun testServiceInvalidatesOnWorkspaceChange() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Point the service to our temp workspace file
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Ensure any previous cached value computed in other tests is invalidated
        pnpmWorkspaceService.refresh()

        // Initial parse and assertion
        pnpmWorkspaceService.parsePnpmWorkspace()
        assertEquals("^18.3.1", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))

        // Modify the workspace YAML react version
        val ioFile = File(workspaceFile.path)
        val updatedYaml = """
            packages:
              - packages/*

            catalog:
              react: ^18.4.0
              redux: ^5.0.1

            catalogs:
              react17:
                react: ^17.0.2
                react-dom: ^17.0.2

              react18:
                react: ^18.2.0
                react-dom: ^18.2.0
        """.trimIndent()
        ioFile.writeText(updatedYaml)

        // Refresh VFS and trigger service refresh to bump its invalidation tracker
        LocalFileSystem.getInstance().refresh(false)
        pnpmWorkspaceService.refresh()

        // Assert the new value is visible
        assertEquals("^18.4.0", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))
    }

    /**
     * Tests that VFS watcher service (not Project as Disposable) observes YAML changes
     * without calling service.refresh() explicitly.
     */
    fun testVfsWatcherAutoRefreshesOnYamlChange() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Point the service to our temp workspace file
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Initialize watcher service (this owns the message bus connection)
        project.service<com.github.skoch13.pnpmcataloglens.listeners.PnpmWorkspaceVfsWatcher>()

        // Initial parse
        pnpmWorkspaceService.refresh()
        pnpmWorkspaceService.parsePnpmWorkspace()
        assertEquals("^18.3.1", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))

        // Modify YAML on disk
        val ioFile = File(workspaceFile.path)
        val updatedYaml = """
            packages:
              - packages/*

            catalog:
              react: ^18.5.0
              redux: ^5.0.1
        """.trimIndent()
        ioFile.writeText(updatedYaml)

        // Only refresh VFS; do NOT call service.refresh() — watcher should trigger it
        LocalFileSystem.getInstance().refresh(false)

        // Allow a tiny window for the async VFS event to propagate
        // (BasePlatformTestCase runs on EDT; polling is acceptable for a simple smoke test)
        val deadline = System.currentTimeMillis() + 2000
        var resolved: String? = null
        while (System.currentTimeMillis() < deadline) {
            resolved = pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:")
            if (resolved == "^18.5.0") break
            Thread.sleep(50)
        }

        assertEquals("^18.5.0", resolved)
    }

    /**
     * Tests behavior when the workspace YAML is deleted/renamed: cache must invalidate and lookups return null,
     * and folding builder should not produce any descriptors.
     */
    fun testServiceHandlesWorkspaceDeletionOrRename() {
        val pnpmWorkspaceService = project.service<PnpmWorkspaceService>()

        // Point the service to our temp workspace file
        val field = PnpmWorkspaceService::class.java.getDeclaredField("workspaceFile")
        field.isAccessible = true
        field.set(pnpmWorkspaceService, workspaceFile)

        // Initial parse
        pnpmWorkspaceService.parsePnpmWorkspace()
        assertEquals("^18.3.1", pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))

        // Delete the YAML file (simulate rename/delete)
        val ioFile = File(workspaceFile.path)
        assertTrue(ioFile.delete())

        // Refresh VFS and bump service invalidation
        LocalFileSystem.getInstance().refresh(false)
        pnpmWorkspaceService.refresh()

        // After deletion, service should not resolve versions anymore
        assertNull(pnpmWorkspaceService.resolveCatalogVersion("react", "catalog:"))

        // Folding builder should produce no descriptors when no workspace is present
        val psiFile = myFixture.configureByText(
            "package.json",
            """
            {
              "dependencies": {
                "react": "catalog:"
              }
            }
            """.trimIndent()
        )

        val builder = PnpmCatalogFoldingBuilder()
        val document = myFixture.editor.document
        val descriptors = builder.buildFoldRegions(psiFile, document, true)
        assertEquals(0, descriptors.size)
    }
}
