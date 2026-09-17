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
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitHookInstallerTests {
  @TempDir lateinit var hooksDir: Path

  private val managedContent = "#!/usr/bin/env bash\n${GitHookInstaller.MARKER}\necho managed\n"
  private val foreignContent = "#!/bin/sh\necho somebody else's hook\n"

  @Test
  fun `installs hook into empty directory as executable file`() {
    val result = GitHookInstaller(hooksDir).install("commit-msg", managedContent, force = false)

    assertInstanceOf(GitHookInstaller.Result.Installed::class.java, result)
    val hook = hooksDir.resolve("commit-msg")
    assertEquals(managedContent, hook.readText())
    assertTrue(hook.isExecutable(), "hook must be executable")
  }

  @Test
  fun `creates missing hooks directory`() {
    val nested = hooksDir.resolve("does/not/exist/yet")

    GitHookInstaller(nested).install("commit-msg", managedContent, force = false)

    assertTrue(nested.resolve("commit-msg").exists())
  }

  @Test
  fun `updates hook previously installed by this plugin`() {
    hooksDir
        .resolve("commit-msg")
        .writeText("#!/usr/bin/env bash\n${GitHookInstaller.MARKER}\necho old\n")

    val result = GitHookInstaller(hooksDir).install("commit-msg", managedContent, force = false)

    assertInstanceOf(GitHookInstaller.Result.Updated::class.java, result)
    assertEquals(managedContent, hooksDir.resolve("commit-msg").readText())
  }

  @Test
  fun `does not touch foreign hook without force`() {
    hooksDir.resolve("commit-msg").writeText(foreignContent)

    val result = GitHookInstaller(hooksDir).install("commit-msg", managedContent, force = false)

    assertInstanceOf(GitHookInstaller.Result.Skipped::class.java, result)
    assertEquals(foreignContent, hooksDir.resolve("commit-msg").readText())
    assertEquals(1, hooksDir.listDirectoryEntries().size, "no backup or other files expected")
  }

  @Test
  fun `replaces foreign hook with force and keeps a backup`() {
    hooksDir.resolve("commit-msg").writeText(foreignContent)

    val result = GitHookInstaller(hooksDir).install("commit-msg", managedContent, force = true)

    val replaced = assertInstanceOf(GitHookInstaller.Result.Replaced::class.java, result)
    assertEquals(managedContent, hooksDir.resolve("commit-msg").readText())
    assertTrue(replaced.backup.exists(), "backup ${replaced.backup} should exist")
    assertTrue(replaced.backup.fileName.toString().startsWith("commit-msg.backup-"))
    assertEquals(foreignContent, replaced.backup.readText())
  }

  @Test
  fun `unchanged managed hook is reported as up to date`() {
    hooksDir.resolve("commit-msg").writeText(managedContent)

    val result = GitHookInstaller(hooksDir).install("commit-msg", managedContent, force = false)

    assertInstanceOf(GitHookInstaller.Result.UpToDate::class.java, result)
  }

  @Test
  fun `rejects content without marker`() {
    val installer = GitHookInstaller(hooksDir)

    val exception =
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
          installer.install("commit-msg", foreignContent, force = false)
        }

    assertTrue(exception.message!!.contains("marker"))
    assertFalse(hooksDir.resolve("commit-msg").exists())
  }
}
