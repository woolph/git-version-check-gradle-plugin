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

import org.eclipse.jgit.api.Status
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "it depends on the state of the git repository")
abstract class GitCleanCheckTask : DefaultTask(), GitRepoAware {
  init {
    group = "verification"
    description =
        "Checks whether the worktree is clean, otherwise it fails the build. This can be used before publishing an artifact to ensure the checkGitVersion is correct."
    gitDirectory.convention(project.layout.projectDirectory.dir(".git"))
  }

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract override val gitDirectory: DirectoryProperty

  @TaskAction
  fun checkGitClean() {
    val status: Status = useGitRepo { git -> git.status().call() }

    if (!status.isClean) {
      throw VerificationException("Git worktree is not clean!\n${status.format()}")
    }
  }

  private fun Status.format(): String =
      sequenceOf(
              added.map { "- added       $it" },
              changed.map { "- changed     $it" },
              modified.map { "- modified     $it" },
              removed.map { "- removed     $it" },
              missing.map { "- missing     $it" },
              untracked.map { "- untracked   $it" },
              conflicting.map { "- conflicting $it" },
          )
          .flatten()
          .joinToString("\n")
}
