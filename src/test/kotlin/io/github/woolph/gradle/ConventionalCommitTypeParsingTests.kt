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

import io.github.woolph.gradle.ConventionalCommitType.Companion.CHECK_CONVENTIONAL_COMMIT
import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.revwalk.RevCommit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ConventionalCommitTypeParsingTests {
  @TestFactory
  fun `UpdateType from works`() =
      sequenceOf(
              "feat: test" to UpdateType.MINOR,
              "fix: test" to UpdateType.PATCH,
              "perf: test" to UpdateType.PATCH,
              "chore: test" to UpdateType.NOTHING,
              "ci: test" to UpdateType.NOTHING,
              "ops: test" to UpdateType.NOTHING,
              "build: test" to UpdateType.NOTHING,
              "style: test" to UpdateType.NOTHING,
              "tests: test" to UpdateType.NOTHING,
          )
          .map { (message, expectedUpdateType) ->
            DynamicTest.dynamicTest("commit message $message yields $expectedUpdateType") {
              val mockRevCommit =
                  mockk<RevCommit> {
                    every { firstMessageLine } returns message.lines()[0]
                    every { fullMessage } returns message
                  }

              assertEquals(expectedUpdateType, UpdateType.from(mockRevCommit, UpdateType.NOTHING))
            }
          }

  @TestFactory
  fun `check for conventional commit`() =
      sequenceOf(
              "feat: test" to UpdateType.MINOR,
              "fix: test" to UpdateType.PATCH,
              "perf: test" to UpdateType.PATCH,
              "chore: test" to UpdateType.NOTHING,
              "ci: test" to UpdateType.NOTHING,
              "ops: test" to UpdateType.NOTHING,
              "build: test" to UpdateType.NOTHING,
              "style: test" to UpdateType.NOTHING,
              "test: test" to UpdateType.NOTHING,
          )
          .map { (message, expectedUpdateType) ->
            DynamicTest.dynamicTest("commit message $message yields $expectedUpdateType") {
              assertTrue(CHECK_CONVENTIONAL_COMMIT.containsMatchIn(message))
            }
          }
}
