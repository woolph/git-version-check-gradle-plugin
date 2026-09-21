# git-version-check-gradle-plugin

[![Gradle plugin](https://img.shields.io/badge/plugins.gradle.org-io.github.woolph.git--version--check-blue.svg)](https://plugins.gradle.org/plugin/io.github.woolph.git-version-check)
[![Changelog](https://img.shields.io/badge/changelog-0.2.0-blue.svg)](CHANGELOG.md)

`git-version-check` is a Gradle plugin that uses the git commit history to determine the version by looking for
['conventional commits'](https://www.conventionalcommits.org/en/v1.0.0/)-based commit messages and checks whether
`project.version` does align with that version, otherwise it fails the build. This is useful if you want to ensure that
the version is bumped correctly to adhere to [semantic versioning](https://semver.org/spec/v2.0.0.html).

## Quick setup

Add the plugin to your Gradle build:

```kotlin
plugins {
  id("io.github.woolph.git-version-check") version "0.2.2"
}
```

That's all! The check runs as part of the `check` task without any further configuration.

## Task wiring

The plugin registers the following task chain:

```
check
└── checkGitVersion             (determines the version by scanning the commit history and checks whether the project.version aligns with that)
    └── checkGitCleanIfRequired (checks whether the git worktree is clean if a dirty worktree is disallow, which is the case by default)
checkGitClean                   (checks whether the git worktree is clean)
printVersion                    (prints the currently set verison)
installGitHooks                 (installs client-side git hooks matching the plugin's commit message rules)
```

## Configuration

All options live inside a `gitVersionCheck {}` block:

```kotlin filename=build.gradle.kts
gitVersionCheck {
  gitDirectory = rootProject.layout.projectDirectory.dir(".git")
  mainBranch = "maestro"
}
```

## Git hooks

`installGitHooks` installs three client-side hooks so that problems are caught before the CI does:

| Hook         | Purpose                                                                                                                                                 |
|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `commit-msg` | Rejects commit messages which are not conventional commits. The accepted commit types are generated from the plugin, so hook and check always agree.   |
| `pre-commit` | Runs `./gradlew spotlessCheck` on the staged changes (skipped if the repository has no gradle wrapper or no `spotlessCheck` task).                     |
| `pre-push`   | Runs `./gradlew checkGitVersion` (with `-PsquashMerge` on feature branches; skipped if the repository has no gradle wrapper or the plugin is not applied). |

```bash
# install into .git/hooks of this repository (default)
./gradlew installGitHooks

# install into the user global hooks directory (core.hooksPath of your user git config;
# if not configured yet, ~/.git/hooks is used and core.hooksPath is set accordingly)
./gradlew installGitHooks -PgitVersionCheck.hookTarget=UserGlobal

# replace existing hooks which were not installed by this plugin (a backup is kept next to them)
./gradlew installGitHooks -PgitVersionCheck.forceHookInstall
```

Hooks installed by this task contain a marker line and are updated on every run. Hooks without that marker are left
untouched (and reported) unless `-PgitVersionCheck.forceHookInstall` is passed. The target can also be set in the build
script via `tasks.installGitHooks { target = GitHookTarget.UserGlobal }`.

> [!NOTE]
> If your user git config sets `core.hooksPath`, git ignores hooks inside `.git/hooks`. The task warns about this
> when installing with the `Local` target.

## Prerequisites

> [!IMPORTANT]
> This plugin relies on a Git commit history which adheres
> to [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)

By default, the supported commit types are:

* **build**: Changes that affect the build system or external dependencies (example scopes: gulp, broccoli, npm)
* **ci**: Changes to our CI configuration files and scripts
* **ops**: Commits that affect operational aspects like infrastructure (IaC), deployment scripts, CI/CD pipelines,
  backups, monitoring, or recovery procedures, ...
* **docs**: Documentation only changes
* **feat**: A new feature
* **fix**: A bug fix
* **perf**: A code change that improves performance
* **refactor**: A code change that neither fixes a bug nor adds a feature
* **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)
* **test**: Adding missing tests or correcting existing tests
* **chore**: Commits that represent tasks like initial commit, modifying .gitignore, ...

> [!NOTE]
> If your git history contains legacy commits where the commit message does not adhere to the conventional commits spec,
> you may add an annotated tag matching the assigned pattern (by default the glob pattern is `v*`)
> define a baselineCommit and override the initialVersion property or to use the plugin.

### Add annotated tag

First of all, you'll have to determine the commit id (or commit hash) of the latest commit which is not yet adhering to
the conventional commits spec and execute the `git tag` command. E.g.:

```shell
git tag -a v0.3.2 ed2eb34e -s -m "baseline for git versioning"
```

### Setting baselineCommit & initialVersion

First of all, you'll have to determine the commit id (or commit hash) of the latest commit which is not yet adhering to
the conventional commits spec and assign the abbreviated or long version of the id to the optional task input property
`baselineCommit`. You also need to override the task input property `initialVersion` (by default it is set to `"0.1.0"`)
to the version this latest legacy commit represents.

```kotlin filename=build.gradle.kts
tasks.checkGitVersion {
  baselineCommit = "ed2eb34e"
  initialVersion = "0.3.2"
}
```

## Compatibility

This plugin supports Gradle versions starting with Gradle 8.14.5.

This plugin fully supports [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html).
[Build Cache](https://docs.gradle.org/current/userguide/build_cache.html) would be supported if applicable, but
currently it's considered not feasible for these tasks.

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
