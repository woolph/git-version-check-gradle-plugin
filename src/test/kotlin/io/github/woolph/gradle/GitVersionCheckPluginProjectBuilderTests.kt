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

import io.github.woolph.gradle.hooks.GitHookTarget
import io.github.woolph.gradle.hooks.InstallGitHooksTask
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * In-process unit tests using [ProjectBuilder]. Unlike the TestKit-based tests, these run inside
 * the same JVM (no Gradle daemon, no separate build), which makes them very fast. They are well
 * suited for verifying the plugin's *wiring* (extensions created, tasks registered, task
 * dependencies, convention defaults) that happens eagerly at apply-time.
 */
class GitVersionCheckPluginProjectBuilderTests {
  private lateinit var project: org.gradle.api.Project

  @BeforeEach
  fun setup() {
    project =
        ProjectBuilder.builder().build().also {
          it.group = "io.github.woolph.test.sub"
          // the plugin wires into the `check` task, which is provided by the java/base plugin
          it.plugins.apply("java")
          it.plugins.apply(GitVersionCheckPlugin::class.java)
        }
  }

  @Test
  fun `applying the plugin creates the gitVersionCheck extension`() {
    val gitVersionCheck = project.extensions.findByName("gitVersionCheck")
    assertNotNull(gitVersionCheck)
    assertTrue(gitVersionCheck is GitVersionCheckExtension)
  }

  @Test
  fun `applying the plugin registers all git-version-check tasks`() {
    listOf(
            "checkGitVersion",
            "checkGitCleanIfRequired",
            "checkGitClean",
            "printVersion",
            "installGitHooks",
        )
        .forEach { taskName ->
          assertNotNull(project.tasks.findByName(taskName), "task '$taskName' should be registered")
        }
  }

  @Test
  fun `preCommitCheck task depends on checkGitVersion`() {
    val preCommitCheck = project.tasks.getByName("preCommitCheck")

    val checkDependencyNames =
        preCommitCheck.taskDependencies.getDependencies(preCommitCheck).map(Task::getName).toSet()

    assertTrue(
        checkDependencyNames.contains("checkGitVersion"),
        "preCommitCheck should depend on checkGitVersion, but depends on $checkDependencyNames",
    )
  }

  @Test
  fun `checkGitVersion depends on checkGitCleanIfRequired`() {
    val checkGitVersion = project.tasks.getByName("checkGitVersion")

    val dependencyNames =
        checkGitVersion.taskDependencies.getDependencies(checkGitVersion).map(Task::getName).toSet()

    assertTrue(
        dependencyNames.contains("checkGitCleanIfRequired"),
        "checkGitVersion should depend on checkGitCleanIfRequired, but depends on $dependencyNames",
    )
  }

  @Test
  fun `printVersion depends on checkGitVersion`() {
    val printVersion = project.tasks.getByName("printVersion")

    val dependencyNames =
        printVersion.taskDependencies.getDependencies(printVersion).map(Task::getName).toSet()

    assertTrue(
        dependencyNames.isEmpty(),
        "printVersion should not depend on any other task, but depends on $dependencyNames",
    )
  }

  @Test
  fun `gitVersionCheck extension exposes the expected convention defaults`() {
    val gitVersionCheck =
        project.extensions.getByName("gitVersionCheck") as GitVersionCheckExtension

    assertEquals(
        project.layout.projectDirectory.file(".git").asFile,
        gitVersionCheck.gitDirectory.get(),
    )
    assertEquals("main", gitVersionCheck.mainBranch.get())
    assertTrue(gitVersionCheck.isCleanWorkingTreeRequired.get())
  }

  @Test
  fun `installGitHooks defaults to local target without force and is never up to date`() {
    val installGitHooks = project.tasks.getByName("installGitHooks") as InstallGitHooksTask

    assertEquals(GitHookTarget.Local, installGitHooks.target.get())
    assertFalse(installGitHooks.force.get())
    assertEquals("main", installGitHooks.mainBranch.get())
    assertEquals(
        project.layout.projectDirectory.dir(".git").asFile,
        installGitHooks.gitDirectory.get(),
    )
    assertEquals(
        java.io.File(System.getProperty("user.home")),
        installGitHooks.userHome.get().asFile,
    )
    assertFalse(installGitHooks.outputs.upToDateSpec.isSatisfiedBy(installGitHooks))
  }
}
