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

import io.github.woolph.gradle.ConventionalCommitType

/**
 * Renders the git hook scripts shipped in `src/main/resources/git-hooks/`. The scripts are plain
 * bash with `@@NAME@@` placeholders which are replaced here, so that e.g. the commit types accepted
 * by the `commit-msg` hook always match the ones known to [ConventionalCommitType].
 */
object GitHookTemplates {
  val HOOK_NAMES: List<String> = listOf("commit-msg", "pre-commit", "pre-push")

  /** alternation of all conventional commit types in lower case, e.g. `build|ci|feat|fix` */
  fun commitTypesPattern(): String =
      ConventionalCommitType.entries.joinToString("|") { it.name.lowercase() }

  fun render(hookName: String, mainBranch: String): String {
    require(hookName in HOOK_NAMES) { "unknown hook '$hookName', known hooks: $HOOK_NAMES" }

    val template =
        GitHookTemplates::class.java.getResourceAsStream("/git-hooks/$hookName")?.use {
          it.bufferedReader().readText()
        } ?: error("hook template '/git-hooks/$hookName' not found on classpath")

    val placeholders =
        mapOf(
            "MARKER" to GitHookInstaller.MARKER,
            "COMMIT_TYPES" to commitTypesPattern(),
            "MAIN_BRANCH" to mainBranch,
        )

    return placeholders.entries.fold(template) { text, (name, value) ->
      text.replace("@@$name@@", value)
    }
  }
}
