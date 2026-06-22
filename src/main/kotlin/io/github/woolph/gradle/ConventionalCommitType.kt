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

enum class ConventionalCommitType(
    val updateType: UpdateType = UpdateType.NOTHING,
) {
  /** Changes that affect the build system or external dependencies */
  Build,
  /** Changes to our CI configuration files and scripts */
  CI,
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
  ;

  companion object {
    val CHECK_CONVENTIONAL_COMMIT =
        Regex(
            "^(${
      ConventionalCommitType.entries.joinToString("|") { it.name.lowercase() }
    })(\\(\\w+\\))?!?:"
        )
  }
}
