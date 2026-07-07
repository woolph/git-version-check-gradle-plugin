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
package io.github.woolph.gradle

import org.eclipse.jgit.revwalk.RevCommit

enum class ConventionalCommitType(
    val updateType: UpdateType = UpdateType.NOTHING,
) {
  /** Changes that affect the build system or external dependencies */
  Build,
  /** Changes to our CI configuration files and scripts */
  CI,
  /**
   * Commits that affect operational aspects like infrastructure (IaC), deployment scripts, CI/CD
   * pipelines, backups, monitoring, or recovery procedures, ...
   */
  Ops,
  /** Documentation only changes */
  Docs,
  /** A new feature */
  Feat(updateType = UpdateType.MINOR),
  /** A bug fix */
  Fix(updateType = UpdateType.PATCH),
  /** A code change that improves performance */
  Perf(updateType = UpdateType.PATCH),
  /** A code change that neither fixes a bug nor adds a feature */
  Refactor,
  /**
   * Changes that do not affect the meaning of the code (white-space, formatting, missing
   * semicolons, etc.)
   */
  Style,
  /** Adding missing tests or correcting existing tests */
  Test,
  /** Commits that represent tasks like initial commit, modifying .gitignore, ... */
  Chore,
  ;

  companion object {
    internal fun parseConventionalCommitType(revCommit: RevCommit): ParseResult =
        when (val matchResult = CHECK_CONVENTIONAL_COMMIT.find(revCommit.firstMessageLine)) {
          null ->
              ParseError(
                  "message did not match pattern $CHECK_CONVENTIONAL_COMMIT",
                  revCommit.fullMessage,
              )
          else -> {
            val commitTypeString = matchResult.groups[1]!!.value
            val isBreakingChange = matchResult.groups[3]?.value == "!"

            if (
                !isBreakingChange &&
                    revCommit.fullMessage.lines().any { it.startsWith(BREAKING_CHANGE_FOOTER_KEY) }
            )
                ParseError(
                    "message contained 'BREAKING CHANGE' line, " +
                        "but didn't indicate that it contains breaking changes in the first line of the message",
                    revCommit.fullMessage,
                )
            else
                when (
                    val commitType =
                        ConventionalCommitType.entries.firstOrNull {
                          it.name.equals(commitTypeString, ignoreCase = true)
                        }
                ) {
                  null ->
                      ParseError("message contained a unknown commit type", revCommit.fullMessage)
                  else -> ParseSuccess(commitType, isBreakingChange)
                }
          }
        }

    internal const val BREAKING_CHANGE_FOOTER_KEY: String = "BREAKING CHANGE:"

    internal val CHECK_CONVENTIONAL_COMMIT = Regex("^(\\w+)(\\(\\w+\\))?(!)?:")
  }

  sealed interface ParseResult

  data class ParseSuccess(
      val conventionalCommitType: ConventionalCommitType,
      val isBreakingChange: Boolean,
  ) : ParseResult {
    val updateType: UpdateType
      get() =
          if (isBreakingChange) {
            UpdateType.MAJOR
          } else {
            conventionalCommitType.updateType
          }
  }

  data class ParseError(val reason: String, val fullMessage: String) : ParseResult {
    override fun toString(): String =
        "${reason}\ncommitMessage: |\n  ${fullMessage.lines().joinToString("  \n")}\n"
  }
}
