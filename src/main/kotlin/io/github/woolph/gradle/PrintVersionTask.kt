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

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This tasks main purpose is printing information to stdout for CI pipelines")
abstract class PrintVersionTask : DefaultTask() {
  @get:Input private val version = project.version.toString()

  @get:Input abstract val versionFormatter: Property<String>

  init {
    group = "other"
    description = "prints the version with the given formatter"

    versionFormatter.convention(
        project.providers.gradleProperty("projectVersionFormatter").orElse("%s")
    )
  }

  @TaskAction
  fun printVersion() {
    println(versionFormatter.map { it.format(version) }.get())
  }
}
