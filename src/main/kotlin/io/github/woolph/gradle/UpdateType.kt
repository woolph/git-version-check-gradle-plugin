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
import org.semver4j.Semver

enum class UpdateType {
  NOTHING,
  PATCH,
  MINOR,
  MAJOR,
  ;

  fun bump(version: Semver): Semver =
      when (this) {
        NOTHING -> version
        PATCH -> version.nextPatch()
        MINOR -> version.nextMinor()
        MAJOR -> version.nextMajor()
      }

  companion object {
    fun fold(updateType0: UpdateType, updateType1: UpdateType) =
        if (updateType0 > updateType1) updateType0 else updateType1

    fun foldVersion(version: Semver, updateType: UpdateType): Semver = updateType.bump(version)

    fun of(value: String) = UpdateType.valueOf(value.uppercase())

    fun from(revCommit: RevCommit, elseBranchUpdateType: UpdateType): UpdateType =
        when {
          MAJOR_PATTERN.containsMatchIn(revCommit.firstMessageLine) ||
              revCommit.fullMessage.lineSequence().any { it.startsWith("BREAKING CHANGE:") } ->
              MAJOR
          MINOR_PATTERN.containsMatchIn(revCommit.firstMessageLine) -> MINOR
          PATCH_PATTERN.containsMatchIn(revCommit.firstMessageLine) -> PATCH
          else -> elseBranchUpdateType
        }

    private val MAJOR_PATTERN =
        Regex(
            "^(${ConventionalCommitType.entries
              .joinToString("|") { it.name.lowercase() }})(\\(\\w+\\))?!:"
        )
    private val MINOR_PATTERN =
        Regex(
            "^(${ConventionalCommitType.entries.filter { it.updateType == MINOR }
              .joinToString("|") { it.name.lowercase() }})(\\(\\w+\\))?:"
        )
    private val PATCH_PATTERN =
        Regex(
            "^(${ConventionalCommitType.entries.filter { it.updateType == PATCH }
              .joinToString("|") { it.name.lowercase() }})(\\(\\w+\\))?:"
        )
  }
}
