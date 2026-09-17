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

import java.nio.file.Path
import kotlin.io.path.exists
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS

/** resolves the directory git hooks are installed to for each [GitHookTarget] */
object GitHooksDirectoryResolver {
  private const val DEFAULT_USER_GLOBAL_HOOKS_PATH = "~/.git/hooks"

  /**
   * the hooks directory of the repository at [gitDirectory]: `core.hooksPath` of the repository's
   * own config file (relative paths are resolved against the worktree), otherwise `<gitDir>/hooks`
   */
  fun resolveLocal(gitDirectory: Path): Path {
    val configFile = gitDirectory.resolve("config")
    val hooksPath =
        if (configFile.exists()) {
          FileBasedConfig(configFile.toFile(), FS.DETECTED).apply { load() }.hooksPath()
        } else {
          null
        }

    return hooksPath?.let { gitDirectory.parent.resolve(it).normalize() }
        ?: gitDirectory.resolve("hooks")
  }

  /**
   * the user global hooks directory configured via `core.hooksPath` in [userConfig]. If not
   * configured, `~/.git/hooks` is used and persisted in [userConfig].
   */
  fun resolveUserGlobal(userConfig: StoredConfig, userHome: Path): Path {
    val hooksPath =
        userConfig.hooksPath()
            ?: DEFAULT_USER_GLOBAL_HOOKS_PATH.also {
              userConfig.setString(
                  ConfigConstants.CONFIG_CORE_SECTION,
                  null,
                  ConfigConstants.CONFIG_KEY_HOOKS_PATH,
                  it,
              )
              userConfig.save()
            }

    return expandTilde(hooksPath, userHome)
  }

  private fun StoredConfig.hooksPath(): String? =
      getString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_HOOKS_PATH)
          ?.takeIf { it.isNotBlank() }

  private fun expandTilde(path: String, userHome: Path): Path =
      when {
        path == "~" -> userHome
        path.startsWith("~/") -> userHome.resolve(path.removePrefix("~/"))
        else -> userHome.resolve(path)
      }.normalize()
}
