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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GitVersionCheckPluginTests {
  @TestFactory
  fun `project without check task fails`() =
      runTestWithGradleRunner(
          setup = {
            settingsFile.writeText("rootProject.name = \"test-project\"")
            buildFile.writeText(
                """
                plugins {
                    id("io.github.woolph.git-version-check")
                }
                """
                    .trimIndent(),
            )
          }
      ) {
        val result = gradleRunner.withArguments("build").buildAndFail()

        assertTrue(
            result.output.contains(
                "Task with name 'check' not found in root project 'test-project'."
            )
        )
      }

  @TestFactory
  fun `project without git repo fails`() =
      runTestWithGradleRunner(
          setup = {
            setupProject("0.1.0")
          }
      ) {
        val result = gradleRunner.withArguments("check").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(null, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo succeeds`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.1.0")
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with wrong version fails`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.2.0")
          }
      ) {
        val result = gradleRunner.withArguments("check").buildAndFail()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `dirty repo fails`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.1.0")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result = gradleRunner.withArguments("check").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":checkGitCleanIfRequired")?.outcome)
      }

  @TestFactory
  fun `dirty repo with option allowDirtyWorkingTree bumps default major version with SNAPSHOT suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("1.0.0-SNAPSHOT")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result =
            gradleRunner.withArguments("check", "-PallowDirtyWorkingTree", "--stacktrace").build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `dirty repo with option allowDirtyWorkingTree bumps minor version with SNAPSHOT suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.2.0-SNAPSHOT")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result =
            gradleRunner
                .withArguments("check", "-PallowDirtyWorkingTree", "-PdirtyBump=minor")
                .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `dirty repo with option allowDirtyWorkingTree bumps patch version with SNAPSHOT suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.1.1-SNAPSHOT")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result =
            gradleRunner
                .withArguments("check", "-PallowDirtyWorkingTree", "-PdirtyBump=patch")
                .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `dirty repo with option allowDirtyWorkingTree does not bump version with SNAPSHOT suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("0.1.0-SNAPSHOT")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result =
            gradleRunner
                .withArguments("check", "-PallowDirtyWorkingTree", "-PdirtyBump=nothing")
                .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `dirty repo with option allowDirtyWorkingTree bumps major version with beta suffix`() =
      runTestWithGradleRunner(
          setup = {
            setupProjectWithGitRepo("1.0.0-beta")

            projectDir.resolve("new.txt").writeText("test content\n")
          }
      ) {
        val result =
            gradleRunner
                .withArguments(
                    "check",
                    "-PallowDirtyWorkingTree",
                    "-PdirtyBump=major",
                    "-PdirtySuffix=beta",
                )
                .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with fix commit succeeds`() =
      runTestWithGradleRunner(
          setup = {
            val git = setupProjectWithGitRepo("0.1.1")

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("fix: busy spin")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with feat commit succeeds`() =
      runTestWithGradleRunner(
          setup = {
            val git = setupProjectWithGitRepo("0.2.0")

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("feat: new feature xyz")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with breaking change x commit succeeds`() =
      runTestWithGradleRunner(
          setup = {
            val git = setupProjectWithGitRepo("1.0.0")

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("perf(scope)!: test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with unconventional commit fails`() =
      runTestWithGradleRunner(
          setup = {
            val git = setupProjectWithGitRepo("0.1.0")

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").buildAndFail()

        println(result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":checkGitVersion")?.outcome)
      }

  @TestFactory
  fun `clean simple repo with unconventional commit succeeds`() =
      runTestWithGradleRunner(
          setup = {
            val git = setupProjectWithGitRepo("0.2.0")

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result =
            gradleRunner
                .withArguments("check", "-PgitVersionCheck.unconventionalCommitBump=minor")
                .build()

        println(result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `property override via main args works`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo("0.2.0") {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                        unconventionalCommitBump = io.github.woolph.gradle.UpdateType.MAJOR
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result =
            gradleRunner
                .withEnvironment(
                    mapOf(
                        "ORG_GRADLE_PROJECT_GIT_VERSION_CHECK_UNCONVENTIONAL_COMMIT_BUMP" to
                            "patch",
                    )
                )
                .withArguments("check", "-PgitVersionCheck.unconventionalCommitBump=minor")
                .build()

        println(result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `property override via envvar works`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo("0.1.1") {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                        unconventionalCommitBump = io.github.woolph.gradle.UpdateType.MAJOR
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result =
            gradleRunner
                .withEnvironment(
                    mapOf(
                        "ORG_GRADLE_PROJECT_gitVersionCheck.unconventionalCommitBump" to "patch",
                    )
                )
                .withArguments("check")
                .build()

        println(result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
      }

  @TestFactory
  fun `property explicit works`() =
      runTestWithGradleRunner(
          setup = {
            val git =
                setupProjectWithGitRepo("1.0.0") {
                  buildFile.appendText(
                      """

                      gitVersionCheck {
                        unconventionalCommitBump = io.github.woolph.gradle.UpdateType.MAJOR
                      }
                      """
                          .trimIndent()
                  )
                }

            projectDir.resolve("new.txt").writeText("test content\n")

            git.add().addFilepatterns("new.txt").call()
            git.commit()
                .setMessage("test")
                .setAuthor(PersonIdent("Your Name", "your.email@example.com"))
                .call()
          }
      ) {
        val result = gradleRunner.withArguments("check").build()

        println(result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitCleanIfRequired")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkGitVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
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
