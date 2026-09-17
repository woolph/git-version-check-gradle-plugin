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
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GitHookTemplatesTests {
  @TempDir lateinit var tempDir: Path

  @Test
  fun `every hook is rendered with the marker and without leftover placeholders`() {
    GitHookTemplates.HOOK_NAMES.forEach { hookName ->
      val rendered = GitHookTemplates.render(hookName, mainBranch = "main")

      assertTrue(rendered.startsWith("#!/usr/bin/env bash\n"), "$hookName must start with shebang")
      assertTrue(
          rendered.lines().any { it == GitHookInstaller.MARKER },
          "$hookName must contain marker line",
      )
      assertFalse(rendered.contains("@@"), "$hookName contains unreplaced placeholder:\n$rendered")
    }
  }

  @Test
  fun `commit type pattern lists all conventional commit types in lower case`() {
    val expected = ConventionalCommitType.entries.map { it.name.lowercase() }.sorted()

    assertEquals(expected, GitHookTemplates.commitTypesPattern().split("|").sorted())
  }

  @Test
  fun `commit-msg hook contains the commit type pattern`() {
    val rendered = GitHookTemplates.render("commit-msg", mainBranch = "main")

    assertTrue(rendered.contains(GitHookTemplates.commitTypesPattern()))
  }

  @Test
  fun `pre-push hook contains the configured main branch`() {
    val rendered = GitHookTemplates.render("pre-push", mainBranch = "maestro")

    assertTrue(rendered.contains("\"maestro\""))
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "feat: add something",
              "fix(scope): repair something",
              "feat!: breaking",
              "refactor(core)!: breaking with scope",
              "Feat: type is matched case-insensitively like the plugin does",
              "docs: with body\n\nsome body text",
              "feat!: breaking with footer\n\nBREAKING CHANGE: removed api",
          ]
  )
  fun `commit-msg hook accepts conventional commit messages`(message: String) {
    assertEquals(0, runCommitMsgHook(message).exitCode)
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "add something",
              "unknown: not a known type",
              "feat:missing space",
              "feat: ",
              "feat: footer without bang\n\nBREAKING CHANGE: removed api",
          ]
  )
  fun `commit-msg hook rejects unconventional commit messages`(message: String) {
    val result = runCommitMsgHook(message)

    assertEquals(1, result.exitCode, "expected rejection, output was:\n${result.output}")
    assertTrue(result.output.contains("conventionalcommits.org"), "should hint at the spec")
  }

  @Test
  fun `commit-msg hook skips merge commits`() {
    val result = runCommitMsgHook("Merge branch 'feature' into main", mergeInProgress = true)

    assertEquals(0, result.exitCode, "merge commits must not be validated:\n${result.output}")
  }

  private data class HookRun(val exitCode: Int, val output: String)

  private fun runCommitMsgHook(message: String, mergeInProgress: Boolean = false): HookRun {
    assumeTrue(File("/usr/bin/env").exists(), "requires a POSIX environment with bash")

    val repoDir = tempDir.resolve("repo").createDirectories()
    // a minimal fake .git directory is enough for `git rev-parse --verify MERGE_HEAD`
    val gitDir = repoDir.resolve(".git").createDirectories()
    gitDir.resolve("HEAD").writeText("ref: refs/heads/main\n")
    gitDir.resolve("objects").createDirectories()
    gitDir.resolve("refs").createDirectories()
    if (mergeInProgress) {
      gitDir.resolve("MERGE_HEAD").createFile().writeText("0".repeat(40) + "\n")
    }

    val hook = tempDir.resolve("commit-msg")
    hook.writeText(GitHookTemplates.render("commit-msg", mainBranch = "main"))
    hook.toFile().setExecutable(true)

    val messageFile = tempDir.resolve("COMMIT_EDITMSG")
    messageFile.writeText(message + "\n")

    val process =
        ProcessBuilder(hook.toString(), messageFile.toString())
            .directory(repoDir.toFile())
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "hook did not terminate")
    return HookRun(process.exitValue(), output)
  }
}
