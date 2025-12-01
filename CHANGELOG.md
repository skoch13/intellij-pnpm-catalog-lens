<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# pnpm-catalog-lens Changelog

## [Unreleased]

## [0.0.4] - 2025-12-01

### Added

- Test: folding builder placeholder verification for default and named catalogs
- Test: service invalidates when `pnpm-workspace.yaml` is deleted/renamed

### Changed

- Migrate startup hook from `StartupActivity` to `ProjectActivity` and keep registration under `<postStartupActivity>` to follow current SDK guidance
- Remove application activation refresh path in favor of project-scoped VFS listener for `pnpm-workspace.yaml`
- Refactor `PnpmWorkspaceService` to use `CachedValue` with read-action-safe parsing and explicit invalidation tracker
- Folding builder: guard dependencies against missing/invalid workspace file to avoid edge-case NPEs
- ProjectActivity listener: filter VFS events to current project content only
- Trim unused dependencies: remove `org.jetbrains.plugins.yaml` and `JavaScript` from `plugin.xml`; clear `platformBundledPlugins` in Gradle

### Fixed

- Eliminated SDK startup warning about migrating to `ProjectActivity`

### Housekeeping

- Remove unused `PnpmWorkspaceActivationListener.kt`

## [0.0.3] - 2025-07-07

### Added

- Increased compatibility range with 252.* versions

## [0.0.2] - 2025-04-14

### Added

- Increased compatibility range with 251.* versions
- Switched to JVM 21

## [0.0.1]

### Added

- Custom folds that reveal catalog version references
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

[Unreleased]: https://github.com/skoch13/intellij-pnpm-catalog-lens/compare/v0.0.4...HEAD
[0.0.4]: https://github.com/skoch13/intellij-pnpm-catalog-lens/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/skoch13/intellij-pnpm-catalog-lens/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/skoch13/intellij-pnpm-catalog-lens/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/skoch13/intellij-pnpm-catalog-lens/commits/v0.0.1
