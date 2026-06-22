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
import kotlin.use
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException
import org.gradle.work.DisableCachingByDefault
import org.semver4j.Semver

@DisableCachingByDefault(because = "it depends on the state of the git repository")
abstract class GitVersionCheckTask : DefaultTask(), GitRepoAware {
  @get:Input private val version = project.version.toString()

  @get:Input abstract val initialVersion: Property<String>

  @get:Input @get:Optional abstract val baselineCommit: Property<String>

  @get:Input abstract val baselineTagPattern: Property<String>

  @get:Input abstract val baselineTagConsiderUnannotated: Property<Boolean>

  /**
   * determines how unconventional commits pump the version. If unset (the default), unconventional
   * commits are failing the task.
   */
  @get:Input @get:Optional abstract val unconventionalCommitBump: Property<UpdateType>

  /** determines the prerelease branch (if the current branch is the prerelease branch) */
  @get:Input @get:Optional abstract val prereleaseBranch: Property<String>

  /**
   * determine how the determined version is bumped if the worktree is dirty (by default we assume
   * the worst case, can be overridden via property e.g. `-PdirtyBump=MINOR`)
   */
  @get:Input abstract val dirtyWorktreeUpdateType: Property<UpdateType>

  /** determine how the determined version is bumped if the worktree is dirty */
  @get:Input abstract val dirtyWorktreePrereleaseSuffix: Property<String>

  /**
   * determines the target branch where all the changes are intended to be squash merged. If set,
   * the version determination handles commits from the target branch to the HEAD as one commit and
   * picks the version bump with the highest severity. Otherwise, the commits are processed
   * individually resulting in multiple bumps.
   */
  @get:Input @get:Optional abstract val squashMergeTarget: Property<String>

  /** determines where the git repository data resides */
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract override val gitDirectory: DirectoryProperty

  init {
    group = "verification"
    description = "" // TODO

    initialVersion.convention("0.1.0")
    baselineTagPattern.convention("v*")
    gitDirectory.convention(project.layout.projectDirectory.dir(".git"))
    baselineTagConsiderUnannotated.convention(false)

    dirtyWorktreeUpdateType.convention(
        project.providers
            .gradleProperty("dirtyBump")
            .map {
              UpdateType.valueOf(it.uppercase())
            }
            .orElse(UpdateType.MAJOR)
    )
    dirtyWorktreePrereleaseSuffix.convention(
        project.providers.gradleProperty("dirtySuffix").orElse("SNAPSHOT")
    )
  }

  // TODO add feature to treat a branch like `dev` as a prerelease branch (commits there are also
  // squash in terms of version bumps)

  @TaskAction
  fun checkGitVersion() {
    val (projectVersion, determinedGitVersion) =
        useGitRepo { git ->
          val projectVersion = Semver(version)
          val initialVersion =
              initialVersion.map(::Semver).map {
                require(it.preRelease.isEmpty()) {
                  "initialVersion should not contain prerelease information"
                }
                require(it.build.isEmpty()) {
                  "initialVersion should not contain build information"
                }
                it
              }

          val latestTag =
              determineBaselineTag(git).onPresent { (version, commit) ->
                logger.info("baseline tag found: $version => ${commit.id}")
              }

          val baselineCommit = determineBaselineCommit(git, initialVersion)

          val (startingVersion, commits) =
              sequenceOf(latestTag, baselineCommit).firstNotNullOfOrNull {
                it.map { (version, commit) ->
                      val headCommit = git.repository.resolve(Constants.HEAD)
                      version to git.log().addRange(commit, headCommit).call()
                    }
                    .orNull
              } ?: (initialVersion.get() to git.log().all().call())

          val commitsOrdered = commits.reversed().drop(1)

          if (!unconventionalCommitBump.isPresent) {
            requireConventionalCommits(commitsOrdered)
          }

          val commitsSplitResult = splitIntoSquashMerge(git, commitsOrdered)

          val determinedVersion = commitsSplitResult.bumpVersion(startingVersion)

          if (!git.status().call().isClean) {
            val dirtyDeterminedVersion = bumpDirty(determinedVersion)
            if (dirtyDeterminedVersion != determinedVersion) {
              logger.warn(
                  "Worktree is dirty! determinedVersion $determinedVersion -> $dirtyDeterminedVersion"
              )
            }
            projectVersion to dirtyDeterminedVersion
          } else {
            projectVersion to determinedVersion
          }
        }

    if (projectVersion != determinedGitVersion) {
      val message =
          "Git version check failed! project.version = \"$projectVersion\", " +
              "but determinedGitVersion = \"${determinedGitVersion}\""
      logger.error(message)
      throw VerificationException(message)
    } else {
      logger.debug(
        "Git version check passed for project.version = \"{}\", determinedGitVersion = \"{}\"",
        projectVersion,
        determinedGitVersion,
      )
    }
  }

  fun bumpDirty(version: Semver): Semver =
      dirtyWorktreeUpdateType.get().bump(version).let { version ->
        dirtyWorktreePrereleaseSuffix
            .get()
            .takeIf { it.isNotEmpty() }
            ?.let { version.withPreRelease(it) } ?: version
      }

  internal fun determineBaselineTag(git: Git): Provider<Pair<Semver, RevCommit>> =
      baselineTagPattern
          .map { pattern ->
            try {
              val tag =
                  git.describe()
                      .setMatch(pattern)
                      .setTags(baselineTagConsiderUnannotated.get())
                      .setAbbrev(0)
                      .call()
              return@map tag?.removePrefix(pattern.removeSuffix("*"))?.let {
                try {
                  Semver(it)
                } catch (e: IllegalArgumentException) {
                  throw IllegalStateException(
                      "'$version' derived from tag refs/tags/$tag does not resemble a semantic version",
                      e,
                  )
                } to tag
              }
            } catch (e: GitAPIException) {
              logger.debug("unable to find tag", e)
              return@map null
            }
          }
          .map { (version, tag) ->
            try {
              val tagObjectId =
                  git.repository.resolve("refs/tags/$tag")
                      ?: throw IllegalStateException(
                          "refs/tags/$tag could not be resolved although it was found with git describe"
                      )
              // annotated tags resolve to a tag object rather than the commit it points to;
              // RevWalk.parseCommit peels the tag down to the underlying commit (and is a
              // no-op for lightweight tags that already point at a commit)
              val commit = RevWalk(git.repository).use { walk -> walk.parseCommit(tagObjectId) }
              version to commit
            } catch (e: GitAPIException) {
              throw IllegalStateException(
                  "refs/tags/$tag could not be resolved although it was found with git describe",
                  e,
              )
            }
          }

  internal fun determineBaselineCommit(
      git: Git,
      initialVersion: Provider<Semver>,
  ): Provider<Pair<Semver, ObjectId>> =
      baselineCommit.zip(initialVersion) { commit, version ->
        try {
          version to
              (git.repository.resolve(commit)
                  ?: throw InvalidUserDataException("baselineCommit '$commit' does not exist"))
        } catch (e: GitAPIException) {
          throw GradleException("baselineCommit '$commit' cannot be resolved", e)
        }
      }

  internal fun splitIntoSquashMerge(git: Git, commits: List<RevCommit>): SplitResult =
      squashMergeTarget
          .map {
            val mainBranchCommit =
                git.repository.resolve(it)
                    ?: throw IllegalStateException("Squash merge target branch \"${it}\" not found")
            val indexOfMainBranchCommit = commits.indexOfFirst { commit ->
              commit.id == mainBranchCommit
            }
            SplitResult(
                commits = commits.slice(0..indexOfMainBranchCommit),
                commitsToBeSquashed = commits.slice(indexOfMainBranchCommit + 1..<commits.size),
            )
          }
          .getOrElse(SplitResult(commits, emptyList()))

  internal data class SplitResult(
      val commits: List<RevCommit>,
      val commitsToBeSquashed: List<RevCommit>,
  )

  internal fun SplitResult.bumpVersion(startingVersion: Semver): Semver =
      commitsToBeSquashed
          .map(::updateTypeFrom)
          .fold(UpdateType.NOTHING, UpdateType::fold)
          .bump(
              commits.map(::updateTypeFrom).fold(startingVersion, UpdateType::foldVersion),
          )

  internal fun updateTypeFrom(revCommit: RevCommit): UpdateType =
      UpdateType.from(revCommit, unconventionalCommitBump.orNull ?: UpdateType.NOTHING)

  /** @throws VerificationException if commits do not adhere to conventional commits spec */
  internal fun requireConventionalCommits(commits: List<RevCommit>) {
    commits.onEach {
      if (!CHECK_CONVENTIONAL_COMMIT.containsMatchIn(it.firstMessageLine))
          throw VerificationException("commit $it does not adhere to conventional commits")
    }
  }

  internal fun <T : Any> Provider<T>.onPresent(block: (T) -> Unit): Provider<T> = apply {
    if (isPresent) block(get())
  }
}
