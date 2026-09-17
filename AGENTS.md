# AGENTS.md

AI agent instructions for `git-version-check-gradle-plugin`.

## What This Project Is

A Gradle plugin (`io.github.woolph.git-version-check`) that validates `project.version` against the version derived from
the git commit history by scanning [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) and applying
semantic version bump rules.

Plugin ID: `io.github.woolph.git-version-check`  
Group: `io.github.woolph.git-version-check`  
Current version: `0.2.0`

## Common Commands

```bash
# Build
./gradlew build

# Run tests only
./gradlew test

# Run a single test class
./gradlew test --tests "io.github.woolph.gradle.GitVersionCheckPluginProjectBuilderTests"

# Run a single test method (use backtick-quoted names for spaces)
./gradlew test --tests "io.github.woolph.gradle.GitVersionCheckPluginProjectBuilderTests.applying the plugin creates the qualityCheck extension"

# Check (build + test + formatting)
./gradlew check

# Apply spotless formatting
./gradlew spotlessApply

# Publish locally for integration testing
./gradlew publishToMavenLocal
```

## Architecture

The plugin follows the standard Gradle plugin pattern: **Extension → Plugin wiring → Tasks**.

### Core files

| File                                 | Role                                                                                              |
|--------------------------------------|---------------------------------------------------------------------------------------------------|
| `GitVersionCheckPlugin.kt`           | Registers the extension and all tasks; wires `checkGitVersion` into `check`                       |
| `GitVersionCheckExtension.kt`        | DSL configuration block (`gitVersionCheck {}`)                                                    |
| `GitVersionCheckTask.kt`             | Core logic: JGit commit walk → `UpdateType` → semver comparison                                   |
| `GitCleanCheckTask.kt`               | Standalone task that fails if the worktree is dirty                                               |
| `ConventionalCommitType.kt`          | Enum mapping commit type strings to their `UpdateType`                                            |
| `UpdateType.kt`                      | `NOTHING / PATCH / MINOR / MAJOR` enum with regex patterns for commit parsing and fold/bump logic |
| `PrintVersionTask.kt`                | Prints `project.version` with an optional formatter (for CI pipelines)                            |
| `hooks/InstallGitHooksTask.kt`       | Installs the git hooks; resolves the hooks directory per `GitHookTarget`                          |
| `hooks/GitHookTarget.kt`             | Enum `Local / UserGlobal`                                                                         |
| `hooks/GitHookInstaller.kt`          | Pure file logic: marker detection, skip/update/replace-with-backup                                |
| `hooks/GitHookTemplates.kt`          | Renders `src/main/resources/git-hooks/*` replacing `@@NAME@@` placeholders                        |
| `hooks/GitHooksDirectoryResolver.kt` | Resolves `core.hooksPath` for the repository or the user config                                   |

### How version determination works

`GitVersionCheckTask` uses JGit to walk commits from the baseline (annotated tag matching `baselineTagPattern`, or
`baselineCommit` property, or the very first commit) up to HEAD. Each commit's first message line is matched against
three compiled regexes in `UpdateType`:

- `type!: …` or footer `BREAKING CHANGE:` → `MAJOR`
- `feat: …` → `MINOR`
- `fix: …` or `perf: …` → `PATCH`
- anything else → `NOTHING`

Commits are folded with `UpdateType::fold` (take the maximum) and applied in sequence via `UpdateType::foldVersion`. If
`squashMergeTarget` is set, commits since the target branch tip are treated as a single logical commit (max bump applied
once). If the worktree is dirty, the determined version is bumped by `dirtyWorktreeUpdateType` (default `MAJOR`) and
given a prerelease suffix (default `SNAPSHOT`).

### Task wiring

```
check
└── checkGitVersion
    └── checkGitCleanIfRequired   (only runs if isCleanWorkingTreeRequired=true)

checkGitClean                     (standalone, not wired into check, intented to be used for publishing gradle task (to avoid publishing dirty states)
printVersion                      (standalone)
installGitHooks                   (standalone; installs commit-msg, pre-commit, pre-push)
```

### Git hooks

The hook scripts live in `src/main/resources/git-hooks/` as plain bash with `@@MARKER@@`, `@@COMMIT_TYPES@@` and
`@@MAIN_BRANCH@@` placeholders. `GitHookTemplates` renders them; the commit type alternation is derived from
`ConventionalCommitType.entries`, so adding a commit type automatically updates the `commit-msg` hook. Every rendered
hook contains the marker line `# managed-by: io.github.woolph.git-version-check`; `GitHookInstaller` only overwrites
files carrying that marker (or any file when `force` is set, keeping a `<hook>.backup-<timestamp>` copy).

`GitHookTarget.Local` honors a repository-level `core.hooksPath`, else uses `<gitDir>/hooks`. `GitHookTarget.UserGlobal`
reads `core.hooksPath` from `<userHome>/.gitconfig`; if unset it uses `~/.git/hooks` and persists that setting. The
user home is the task property `userHome` (default: system property `user.home`) — tests override it with
`-Duser.home=<tmp>` so they never touch the developer's real `~/.gitconfig`.

### Gradle properties recognized at runtime

| Property                           | Effect                                                            |
|------------------------------------|-------------------------------------------------------------------|
| `allowDirtyWorkingTree`            | Skips `checkGitCleanIfRequired`                                   |
| `squashMerge`                      | Enables squash mode; target is `refs/remotes/origin/<mainBranch>` |
| `squashMergeOnto`                  | Explicit squash target ref (overrides `squashMerge`)              |
| `dirtyBump`                        | Override dirty-worktree bump type (`PATCH`, `MINOR`, `MAJOR`)     |
| `dirtySuffix`                      | Override dirty prerelease suffix (default `SNAPSHOT`)             |
| `gitVersionCheck.hookTarget`       | `Local` (default) or `UserGlobal` for `installGitHooks`           |
| `gitVersionCheck.forceHookInstall` | Replace foreign hooks in `installGitHooks` (backup is kept)       |

## Code Style

Spotless with `ktfmt` is enforced. Run `./gradlew spotlessApply` before committing. Spotless uses
`ratchetFrom("origin/main")` so only files changed relative to `origin/main` are formatted. All source files must carry
the Apache 2.0 license header from `.license-header`.

## Testing

Two test strategies exist:

- **`GitVersionCheckPluginProjectBuilderTests`** — in-process `ProjectBuilder` tests for plugin wiring (fast, no
  daemon). Note: `afterEvaluate` blocks are not triggered by `ProjectBuilder`.
- **`GitVersionCheckPluginTests`** — TestKit functional tests that launch a real Gradle build against temp project
  directories; parameterized over supported Gradle versions (`8.14.5`, `9.5.1`).
- **`hooks/*Tests`** — plain JUnit tests for the hook installer, directory resolver and templates. The template tests
  execute the rendered `commit-msg` hook with bash against sample messages, so they need a POSIX environment.

The initial commit created by the TestKit helpers uses a conventional message (`chore: initial commit`), because JGit
executes `commit-msg` hooks from a user global `core.hooksPath` too, and a developer may have the plugin's hooks
installed globally.

JaCoCo coverage report is generated automatically after `test` (`finalizedBy jacocoTestReport`).

## Key Constraints

- Configuration Cache is fully supported — do not introduce `project` references in task actions.
- Build Cache is intentionally **not** supported (tasks depend on live git state).
- JVM toolchain target: **Java 17**.
- Gradle compatibility: **8.14.5+**.
