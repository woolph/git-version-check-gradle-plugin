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

import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GitVersionCheckPluginPrereleaseTests {
  @TestFactory
  fun `prerelease branch with no version-bumping commits gives plain version without suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo(
                "0.1.0",
                branch = "dev",
            ) {
              buildFile.appendText(
                  """

                  gitVersionCheck {
                      prereleaseBranch = "dev"
                  }
                  """
                      .trimIndent()
              )
            }
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `prerelease branch with single feat commit gives minor version with branch suffix and index 0`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo(
                    "0.2.0-dev.0",
                    branch = "dev",
                ) {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("feature content\n")
            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("feat: new feature")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `prerelease branch with feat then fix gives minor version with index 1`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo(
                    "0.2.0-dev.1",
                    branch = "dev",
                ) {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("feat.txt").writeText("feature\n")
            git.add().addFilepatterns("feat.txt").call()
            git.commit()
                .setMessage("feat: new feature")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()

            projectDir.resolve("fix.txt").writeText("fix\n")
            git.add().addFilepatterns("fix.txt").call()
            git.commit()
                .setMessage("fix: bug fix")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `prerelease branch feat then same severity feat increments index not resets`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo(
                    "0.2.0-dev.2",
                    branch = "dev",
                ) {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("feat1.txt").writeText("feature1\n")
            git.add().addFilepatterns("feat1.txt").call()
            git.commit()
                .setMessage("feat: feature one")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()

            projectDir.resolve("fix.txt").writeText("fix\n")
            git.add().addFilepatterns("fix.txt").call()
            git.commit()
                .setMessage("fix: bug fix")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()

            projectDir.resolve("feat2.txt").writeText("feature2\n")
            git.add().addFilepatterns("feat2.txt").call()
            git.commit()
                .setMessage("feat: feature two")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `prerelease branch severer commit resets index`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo(
                    "1.0.0-dev.1",
                    branch = "dev",
                ) {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("feat.txt").writeText("feature\n")
            git.add().addFilepatterns("feat.txt").call()
            git.commit()
                .setMessage("feat: new feature")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()

            projectDir.resolve("breaking.txt").writeText("breaking\n")
            git.add().addFilepatterns("breaking.txt").call()
            git.commit()
                .setMessage("feat!: breaking change")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()

            projectDir.resolve("fix.txt").writeText("fix\n")
            git.add().addFilepatterns("fix.txt").call()
            git.commit()
                .setMessage("fix: follow-up fix")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `prerelease branch with configurable suffix`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo(
                    "0.2.0-rc.0",
                    branch = "dev",
                ) {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                          prereleaseSuffix = "rc"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("feature content\n")
            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("feat: new feature")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `non-prerelease branch ignores prerelease config`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo("0.2.0") {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                          prereleaseBranch = "dev"
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("feature content\n")
            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("feat: new feature")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
      }

  // TODO do more tests with baselineCommit & baselineTagPattern

  companion object {
    val SUPPORTED_GRADLE_VERSIONS = System.getProperty("SUPPORTED_GRADLE_VERSION").split(",")

    val TEMP_DIR_BASE =
        Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("git-version-chech-gradle-plugin-tests")
            .createDirectories()
  }

  internal fun runTestWithGradleRunner(
      setup: GradleProject.() -> Unit,
      block: GradleProject.() -> Unit,
  ): Sequence<DynamicTest> {
    val projectDir = createTempDirectory(TEMP_DIR_BASE)
    val gradleProject =
        GradleProject(
            projectDir = projectDir,
            gradleRunner =
                GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath(),
        )

    gradleProject.setup()

    return SUPPORTED_GRADLE_VERSIONS.asSequence().map { gradleVersion ->
      DynamicTest.dynamicTest("gradleVersion = $gradleVersion") {
        block(
            gradleProject.apply {
              gradleRunner.withGradleVersion(gradleVersion)
            }
        )
      }
    }
  }

  internal class GradleProject(val projectDir: Path, val gradleRunner: GradleRunner) {
    val settingsFile: Path = projectDir.resolve("settings.gradle.kts")
    val buildFile: Path = projectDir.resolve("build.gradle.kts")
    val gradleProperties: Path = projectDir.resolve("gradle.properties")
    val gitignore: Path = projectDir.resolve(".gitignore")

    fun setupProjectWithGitRepo(
        version: String,
        branch: String = "main",
        additionalSetup: (GradleProject.() -> Unit)? = null,
    ): Git {
      setupProject(version)
      additionalSetup?.invoke(this)

      val git = Git.init().setDirectory(projectDir.toFile()).setInitialBranch(branch).call()
      git.add()
          .addFilepatterns(
              *projectDir.listDirectoryEntries().map { it.fileName.toString() }.toTypedArray()
          )
          .call()
      git.commit()
          .setMessage("Initial commit")
          .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
          .call()

      return git
    }

    fun setupProject(version: String) {
      settingsFile.writeText("rootProject.name = \"test-project\"")
      gitignore.writeText(
          """
          /.gradle/
          """
              .trimIndent()
      )
      gradleProperties.writeText("org.gradle.configuration-cache=true")
      buildFile.writeText(
          """
        plugins {
            base
            id("io.github.woolph.git-version-check")
        }

        version = "$version"
        """
              .trimIndent(),
      )
    }
  }
}
