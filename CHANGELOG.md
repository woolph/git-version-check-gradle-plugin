# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased/Upcoming

## [0.1.6] - 2026-07-07

### Changed

- `printVersion` task now prints with println instead of logger.lifecycle, as this is more appropriate for a task that
  is meant to be used in CI/CD pipelines, because the -q quiet option would otherwise suppress even the version printing

## [0.1.5] - 2026-07-07

### Added

- possibility to ignore merge commits with more than one parent when checking for conventional commits, as these are
  usually not relevant for versioning (active by default)

## [0.1.4] - 2026-07-07

### Changed

- `printVersion` task now prints with logger.lifecycle instead of println

## [0.1.3] - 2026-07-07

### Added

- check to ensure that the git worktree is not a shallow clone, as this would break the version check

### Changed

- `printVersion` task now no longer depends on `checkGitVersion`
- improved logging for better diagnostics

## [0.1.2] - 2026-07-06

### Added

- missing conventional commit types `ops` & `chore`

## [0.1.1] -2026-06-30
