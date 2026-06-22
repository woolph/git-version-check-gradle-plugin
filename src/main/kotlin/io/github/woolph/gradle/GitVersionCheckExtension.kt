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

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.kotlin.dsl.property

abstract class GitVersionCheckExtension @Inject constructor(project: Project) {
  val gitDirectory =
      project.objects.directoryProperty().convention(project.layout.projectDirectory.dir(".git"))

  val mainBranch = project.objects.property<String>().convention("main")

  val unconventionalCommitBump = project.objects.property<UpdateType>()

  val prereleaseBranch = project.objects.property<String>()

  val prereleaseSuffix = project.objects.property<String>()

  /**
   * if set to true (default), the checkGitVersion also checks if the working tree is clean,
   * otherwise this check is skipped. This checks for a Gradle property `allowDirtyWorkingTree`. So,
   * you can overrule the default by passing the following argument
   *
   * ```
   * ./gradlew checkGradleVersion -PallowDirtyWorkingTree
   * ```
   */
  val isCleanWorkingTreeRequired =
      project.objects
          .property<Boolean>()
          .convention(
              project.providers
                  .gradleProperty("allowDirtyWorkingTree")
                  .map { !(it.isEmpty() || it.toBoolean()) }
                  .orElse(true)
          )
}
