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

import java.io.File
import kotlin.use
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.GradleException
import org.gradle.api.provider.Property

interface GitRepoAware {
  val gitDirectory: Property<File>

  @Suppress("detekt:TooGenericExceptionCaught")
  fun <R> useGitRepo(block: (Git) -> R): R =
      try {
        val file = gitDirectory.get()
        val actualDirectory =
            when {
              file.isDirectory -> file
              file.isFile -> File(file.readLines(Charsets.UTF_8).first().removePrefix("gitdir: "))
              else ->
                  throw IllegalStateException(
                      "$file does not exist (or it is neither directory nor a file"
                  )
            }
        FileRepositoryBuilder().setGitDir(actualDirectory).build().use { repository ->
          Git(repository).use { git ->
            block(git)
          }
        }
      } catch (e: GradleException) {
        throw e
      } catch (t: Throwable) {
        throw GradleException("Error checking git version: ${t.message}", t)
      }
}
