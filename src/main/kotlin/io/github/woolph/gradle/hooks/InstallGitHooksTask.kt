/*
 * Copyright ${"$"}YEAR ENGEL Austria GmbH. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// SPDX-License-Identifier: Apache-2.0
package io.github.woolph.gradle.hooks

import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Installs the git hooks shipped with this plugin (see [GitHookTemplates.HOOK_NAMES]) either into
 * the project's repository or into the user's global hooks directory (see [GitHookTarget]).
 *
 * Hooks which were not installed by this plugin are never overwritten unless [force] is set, in
 * which case the existing hook is kept as a backup next to the new one.
 */
@DisableCachingByDefault(because = "it installs files into the git repository or user home")
abstract class InstallGitHooksTask : DefaultTask() {
  /**
   * where to install the hooks to (default `Local`), can be overridden via the Gradle property
   * `gitVersionCheck.hookTarget`, e.g. `-PgitVersionCheck.hookTarget=UserGlobal`
   */
  @get:Input abstract val target: Property<GitHookTarget>

  /**
   * if set to true, hooks which were not installed by this plugin are replaced (a backup is kept),
   * can be enabled via the Gradle property `gitVersionCheck.forceHookInstall`
   */
  @get:Input abstract val force: Property<Boolean>

  /** the branch feature branches are squash merged onto, used by the `pre-push` hook */
  @get:Input abstract val mainBranch: Property<String>

  /** the git directory of the project, only used for [GitHookTarget.Local] */
  @get:Internal abstract val gitDirectory: DirectoryProperty

  /**
   * the user's home directory containing the `.gitconfig` (default: system property `user.home`),
   * used for [GitHookTarget.UserGlobal] and to detect a user global `core.hooksPath`
   */
  @get:Internal abstract val userHome: DirectoryProperty

  init {
    group = "git hooks"
    description =
        "Installs the git hooks (${GitHookTemplates.HOOK_NAMES.joinToString()}) into the local " +
            "repository or the user global hooks directory"

    target.convention(GitHookTarget.Local)
    force.convention(false)
    mainBranch.convention("main")
    gitDirectory.convention(project.layout.projectDirectory.dir(".git"))
    userHome.convention(
        project.layout.dir(project.providers.systemProperty("user.home").map(::File))
    )

    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun installGitHooks() {
    val hooksDirectory = resolveHooksDirectory()
    logger.lifecycle("Installing git hooks into {}", hooksDirectory)

    val installer = GitHookInstaller(hooksDirectory)
    val results =
        GitHookTemplates.HOOK_NAMES.map { hookName ->
          installer.install(
              hookName,
              GitHookTemplates.render(hookName, mainBranch.get()),
              force.get(),
          )
        }

    results.forEach { result ->
      val hookName = result.hookFile.fileName
      when (result) {
        is GitHookInstaller.Result.Installed -> logger.lifecycle("  {}: installed", hookName)
        is GitHookInstaller.Result.Updated -> logger.lifecycle("  {}: updated", hookName)
        is GitHookInstaller.Result.UpToDate -> logger.lifecycle("  {}: up to date", hookName)
        is GitHookInstaller.Result.Replaced ->
            logger.lifecycle("  {}: replaced, backup at {}", hookName, result.backup)
        is GitHookInstaller.Result.Skipped ->
            logger.warn("  {}: skipped, existing hook was not installed by this plugin", hookName)
      }
    }

    if (results.any { it is GitHookInstaller.Result.Skipped }) {
      logger.warn(
          "Some hooks were skipped. Re-run with -PgitVersionCheck.forceHookInstall to replace " +
              "them (the existing hooks are kept as backup)."
      )
    }
  }

  private fun resolveHooksDirectory(): Path =
      when (target.get()) {
        GitHookTarget.Local -> {
          val gitDir = gitDirectory.get().asFile.toPath()
          if (!gitDir.isDirectory()) {
            throw InvalidUserDataException("git directory $gitDir does not exist")
          }
          warnIfShadowedByUserGlobalHooksPath()
          GitHooksDirectoryResolver.resolveLocal(gitDir)
        }
        GitHookTarget.UserGlobal ->
            GitHooksDirectoryResolver.resolveUserGlobal(loadUserConfig(), userHomePath())
      }

  private fun userHomePath(): Path = userHome.get().asFile.toPath()

  private fun loadUserConfig(): FileBasedConfig =
      FileBasedConfig(userHomePath().resolve(".gitconfig").toFile(), FS.DETECTED).apply { load() }

  /** hooks inside the repository are ignored by git if the user config sets `core.hooksPath` */
  private fun warnIfShadowedByUserGlobalHooksPath() {
    loadUserConfig()
        .getString("core", null, "hooksPath")
        ?.takeIf { it.isNotBlank() }
        ?.let {
          logger.warn(
              "Your user git config sets core.hooksPath = {}; git ignores hooks inside the repository " +
                  "while this is set. Consider -PgitVersionCheck.hookTarget=UserGlobal instead.",
              it,
          )
        }
  }
}
