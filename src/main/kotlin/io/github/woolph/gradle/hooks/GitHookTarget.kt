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

/** where the `installGitHooks` task installs the git hooks to */
enum class GitHookTarget {
  /**
   * the hooks directory of the project's git repository (`core.hooksPath` of the repository config
   * if set, otherwise `.git/hooks`)
   */
  Local,

  /**
   * the user global hooks directory configured via `core.hooksPath` in the user's git config; if
   * not configured yet, `~/.git/hooks` is used and `core.hooksPath` is set accordingly
   */
  UserGlobal,
  ;

  companion object {
    fun of(value: String): GitHookTarget =
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "unknown git hook target '$value', expected one of ${entries.joinToString()}"
            )
  }
}
