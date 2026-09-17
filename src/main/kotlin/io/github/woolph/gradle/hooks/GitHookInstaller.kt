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

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Writes git hook scripts into a hooks directory without clobbering hooks that were not created by
 * this plugin. Hooks created by this plugin are recognized by the [MARKER] line they contain.
 */
class GitHookInstaller(private val hooksDirectory: Path) {

  sealed interface Result {
    val hookFile: Path

    /** no hook existed before */
    data class Installed(override val hookFile: Path) : Result

    /** a hook managed by this plugin existed and was overwritten with new content */
    data class Updated(override val hookFile: Path) : Result

    /** a hook managed by this plugin existed and already had the desired content */
    data class UpToDate(override val hookFile: Path) : Result

    /** a hook not managed by this plugin exists and was left untouched */
    data class Skipped(override val hookFile: Path) : Result

    /** a hook not managed by this plugin was moved to [backup] and then overwritten */
    data class Replaced(override val hookFile: Path, val backup: Path) : Result
  }

  fun install(hookName: String, content: String, force: Boolean): Result {
    require(content.contains(MARKER)) {
      "hook content for '$hookName' does not contain the marker line '$MARKER'"
    }

    val hookFile = hooksDirectory.resolve(hookName)

    return when {
      !hookFile.exists() -> {
        hooksDirectory.createDirectories()
        hookFile.writeExecutable(content)
        Result.Installed(hookFile)
      }
      hookFile.isManaged() -> {
        if (hookFile.readText() == content) {
          Result.UpToDate(hookFile)
        } else {
          hookFile.writeExecutable(content)
          Result.Updated(hookFile)
        }
      }
      force -> {
        val backup =
            hooksDirectory.resolve(
                "$hookName.backup-${LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT)}"
            )
        hookFile.moveTo(backup)
        hookFile.writeExecutable(content)
        Result.Replaced(hookFile, backup)
      }
      else -> Result.Skipped(hookFile)
    }
  }

  private fun Path.isManaged(): Boolean = readText().lineSequence().any { it.trim() == MARKER }

  private fun Path.writeExecutable(content: String) {
    writeText(content)
    if (!Files.isExecutable(this)) {
      toFile().setExecutable(true, false)
    }
  }

  companion object {
    /** line that identifies hooks installed by this plugin */
    const val MARKER = "# managed-by: io.github.woolph.git-version-check"

    private val BACKUP_TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
  }
}
