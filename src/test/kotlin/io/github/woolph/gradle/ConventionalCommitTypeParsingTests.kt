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

import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.revwalk.RevCommit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail

class ConventionalCommitTypeParsingTests {
  @TestFactory
  fun `parseConventionalCommitType from works`() =
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
              "feat!: test" to UpdateType.MAJOR,
              "fix!: test" to UpdateType.MAJOR,
              "perf!: test" to UpdateType.MAJOR,
              "chore!: test" to UpdateType.MAJOR,
              "ci!: test" to UpdateType.MAJOR,
              "ops!: test" to UpdateType.MAJOR,
              "build!: test" to UpdateType.MAJOR,
              "style!: test" to UpdateType.MAJOR,
              "test!: test" to UpdateType.MAJOR,
              """
              test!: bla bla

              BREAKING CHANGE: API has changed in a backwards incompatible way.
              """
                  .trimIndent() to UpdateType.MAJOR,
          )
          .map { (fullMessage, expectedUpdateType) ->
            DynamicTest.dynamicTest("commit message $fullMessage yields $expectedUpdateType") {
              val mockRevCommit = mockRevCommit(fullMessage)

              when (
                  val parseResult =
                      ConventionalCommitType.parseConventionalCommitType(mockRevCommit)
              ) {
                is ConventionalCommitType.ParseError ->
                    fail { "Failed to parse commit message: $parseResult" }
                is ConventionalCommitType.ParseSuccess ->
                    assertEquals(expectedUpdateType, parseResult.updateType)
              }
            }
          }

  @TestFactory
  fun `parseConventionalCommitType detects unconventional commit`() =
      sequenceOf(
              "just rambling in the commit message with no structure",
              """
              test: bla bla

              BREAKING CHANGE: API has changed in a backwards incompatible way.
              """
                  .trimIndent(),
          )
          .map { fullMessage ->
            DynamicTest.dynamicTest(
                "commit message $fullMessage is detected as 'unconventional' commit"
            ) {
              val mockRevCommit = mockRevCommit(fullMessage)

              assertTrue(
                  ConventionalCommitType.parseConventionalCommitType(mockRevCommit)
                      is ConventionalCommitType.ParseError
              ) {
                "Parse result should be ParseError for commit message: $fullMessage"
              }
            }
          }

  internal fun mockRevCommit(fullMessage: String) =
      mockk<RevCommit> {
        every { this@mockk.firstMessageLine } returns fullMessage.lines()[0]
        every { this@mockk.fullMessage } returns fullMessage
      }
}
