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

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitHooksDirectoryResolverTests {
  @TempDir lateinit var tempDir: Path

  private val worktree: Path
    get() = tempDir.resolve("repo")

  private val gitDir: Path
    get() = worktree.resolve(".git")

  private val userHome: Path
    get() = tempDir.resolve("home")

  private fun userConfig(content: String? = null): FileBasedConfig {
    userHome.createDirectories()
    val file = userHome.resolve(".gitconfig")
    content?.let(file::writeText)
    return FileBasedConfig(file.toFile(), FS.DETECTED).apply { load() }
  }

  @Test
  fun `local target defaults to hooks directory inside git directory`() {
    gitDir.createDirectories()

    assertEquals(gitDir.resolve("hooks"), GitHooksDirectoryResolver.resolveLocal(gitDir))
  }

  @Test
  fun `local target honors relative core hooksPath from repository config`() {
    gitDir.createDirectories()
    gitDir.resolve("config").writeText("[core]\n\thooksPath = .githooks\n")

    assertEquals(worktree.resolve(".githooks"), GitHooksDirectoryResolver.resolveLocal(gitDir))
  }

  @Test
  fun `local target honors absolute core hooksPath from repository config`() {
    gitDir.createDirectories()
    val absolute = tempDir.resolve("elsewhere")
    gitDir.resolve("config").writeText("[core]\n\thooksPath = $absolute\n")

    assertEquals(absolute, GitHooksDirectoryResolver.resolveLocal(gitDir))
  }

  @Test
  fun `user global target uses configured core hooksPath and expands tilde`() {
    val config = userConfig("[core]\n\thooksPath = ~/my-hooks\n")

    assertEquals(
        userHome.resolve("my-hooks"),
        GitHooksDirectoryResolver.resolveUserGlobal(config, userHome),
    )
  }

  @Test
  fun `user global target without core hooksPath falls back and persists it in the user config`() {
    val config = userConfig()

    val resolved = GitHooksDirectoryResolver.resolveUserGlobal(config, userHome)

    assertEquals(userHome.resolve(".git/hooks"), resolved)
    val saved = userHome.resolve(".gitconfig").readText()
    assertTrue(saved.contains("hooksPath"), "user config should now contain hooksPath:\n$saved")
    assertTrue(saved.contains("~/.git/hooks"), "hooksPath should be stored home-relative:\n$saved")
  }
}
