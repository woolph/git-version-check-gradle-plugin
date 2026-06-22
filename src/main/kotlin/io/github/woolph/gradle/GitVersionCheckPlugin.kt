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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class GitVersionCheckPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.run {
      val gitVersionCheckExtension =
          extensions.create("gitVersionCheck", GitVersionCheckExtension::class)

      tasks.register<GitCleanCheckTask>("checkGitClean") {
        gitDirectory.convention(gitVersionCheckExtension.gitDirectory)
      }

      val checkGitCleanIfRequired =
          tasks.register<GitCleanCheckTask>("checkGitCleanIfRequired") {
            gitDirectory.set(gitVersionCheckExtension.gitDirectory)
            gitDirectory.disallowChanges()

            onlyIf { gitVersionCheckExtension.isCleanWorkingTreeRequired.get() }
          }

      val checkGitVersion =
          tasks.register<GitVersionCheckTask>("checkGitVersion")  {
            dependsOn(checkGitCleanIfRequired)

            gitDirectory.set(gitVersionCheckExtension.gitDirectory)
            gitDirectory.disallowChanges()

            squashMergeTarget.set(
                project.providers
                    .gradleProperty("squashMergeOnto")
                    .orElse(
                        project.providers.gradleProperty("squashMerge").zip(
                            gitVersionCheckExtension.mainBranch
                        ) { _, mainBranch ->
                          "refs/remotes/origin/$mainBranch"
                        }
                    )
            )
            squashMergeTarget.disallowChanges()

            unconventionalCommitBump.set(project.providers
              .gradleProperty("gitVersionCheck.unconventionalCommitBump")
              .map(UpdateType::of).orElse(gitVersionCheckExtension.unconventionalCommitBump))
            unconventionalCommitBump.disallowChanges()
          }

      tasks.register<PrintVersionTask>("printVersion") {
        dependsOn(checkGitVersion)
      }

      tasks.named("check") {
        dependsOn(checkGitVersion)
      }
    }
  }
}
