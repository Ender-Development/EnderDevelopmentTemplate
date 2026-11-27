# Ender-Development.org Template Changelog

<!--
Writing a changelog is a good practice for maintaining a clear history of changes in your project.
Try to follow the [Common Changelog](https://common-changelog.org/) conventions.
-->

## [Unreleased]

### Additions

### Changes

### Bugfixes

### Miscellaneous

## [2.4.4] - 2026-11-27

### Bugfixes

- delete files before upload

## [2.4.3] - 2026-11-27

### Bugfixes

- class instance order
- providing file to publishing
- catalyx enabled by default

### Miscellaneous

- update PROPERTIES.md docs

## [2.4.2] - 2026-11-26

### Changes

- build(deps): bump com.diffplug.spotless from 8.11.0 to 8.12.0 (#8)
- build(deps): bump zone.rong:mixinbooter from 12.4 to 12.5 (#9)
- build(deps): bump zone.rong.clearskies from 2.0.0 to 2.1.0 (#10)

### Bugfixes

- special version handling for catalyx

## [2.4.1] - 2026-11-22

### Bugfixes

- mod not loading

### Miscellaneous

- cleanup

## [2.4.0] - 2026-11-22

### Additions

- auto gradle update

### Changes

- refactor variable storage and access
- refactor *.properties file loading
- move prop utils to dedicated class

### Bugfixes

- property doc generation
- allow file header with spotless off
- also sync `libs.versions.toml`

### Miscellaneous

- fold section for build.gradle.kts setup section
- bundle utility functions
- reference tasks my variable instead of string
- narrow down repository scope
- update prop-docs
- move all prop calls to PropertyManager
- bump gradle 9.9.1 -> 9.9.2

## [2.3.4] - 2026-11-20

### Changes

- build(deps): bump zone.rong:mixinbooter from 12.3 to 12.4 (#7)

### Bugfixes

- set jvmToolchain version to 25 instead of 8

### Miscellaneous

- ignore all kotlin patches via dependabot.yml

## [2.3.3] - 2026-11-20

### Additions

- version gh workflows

### Changes

- remove non-essential dependabot registries
- remove non-essential maven repositories
- Update deploy.yml

### Miscellaneous

- update maven example in `dependencies.properties`
- formatting
- lazy load a few variables

## [2.3.2] - 2026-11-19

### Changes

- build(deps): bump mezz:jei from 4.37.2 to 4.38.1 (#5)
- build(deps): bump futbol.rozbrajacz:rozutils from 0.4.0 to 0.5.0 (#4)

### Bugfixes

- deploy.yml using the wrong version

### Miscellaneous

- let dependency-submission.yml chmod gradlew

## [2.3.1] - 2026-11-19

### Additions

- missing registries to dependabot.yml

### Changes

- build(deps): bump the kotlin group with 4 updates (#2)
- build(deps): bump the actions group with 2 updates (#1)

### Bugfixes

- lock down kotlin version until next 2.4.3* release

### Miscellaneous

- reduce GitHub API calls when in template

## [2.3.0] - 2026-11-19

### Additions

- dependabot.yml as an experiment
- custom dependency-submission workflow

### Changes

- remove unused properties from docs
- refactor buildscripts to use new versions.toml
- move versions from `gradle.properties` to `libs.versions.toml`

### Bugfixes

- README.md looking for wrong kotlin and gradleversion
- custom dependency-submission workflow branch name
- make gradlew executable

## [2.2.4] - 2026-11-18

### Additions

- insert mixin configs into manifest

### Changes

- auto detect java version
- gate publish task behind maven boolean
- optional maven publishing
- ATs are now a single file per mod

### Bugfixes

- apply ATs at runtime

### Miscellaneous

- check for source before decompress
- cleanup

## [2.2.3] - 2026-11-15

### Additions

- option to use a github token to bypass rate limits

### Bugfixes

- error when enabling ATs

### Miscellaneous

- secrets.example.properties is now versioned

## [2.2.2] - 2026-11-15

### Additions

- additional changelog section

### Changes

- transition to publish via `me.modmuss50.mod-publish-plugin`

### Bugfixes

- error when retrieving changelog for non-existent version

### Miscellaneous

- tone done on the sponsor part in the README.md

## [2.2.1] - 2026-11-13

### Changes

- switch from google-java-format to prince-of-space

## [2.2.0] - 2026-11-13

### Changes

- update README.md
- resolve publishing data via moddex.gg API
- add 'moddex_id' property
- add more badges when project is published
- bump gradle 9.9.0 -> 9.9.1
- hard gate publish tasks to enable shield generation with project id

## [2.1.4] - 2026-11-12

### Changes

- refactor README.md shields section
- let "updateBuildScript" only sync up to latest tag
- add icons to README.md shields

## [2.1.3] - 2026-11-11

### Changes

- simplify template check
- add grs example JSONs to .gitignore
- comment out Tag code, class doesn't generate when using kotlin as language

### Miscellaneous

- apply spotless

## [2.1.2] - 2026-11-11

### Bugfixes

- 'Release Release [...]' commit message

## [2.1.1] - 2026-11-11

### Changes

- update gradle.properties
- add settings.gradle.kts to versioned files

### Miscellaneous

- update gradle 9.8.2 -> 9.9.0
- Unit -> continue

## [2.1.0] - 2026-11-11

### Changes

- add proper build.gradle.kts header
- add .gitattributes to the versioned files
- add .gitignore to the versioned files

### Miscellaneous

- apply formatting to build.gradle.kts
- iterate with forEach instead of for-loop

## [2.0.9] - 2026-11-11

### Changes

- require 'mod_url' property, remove empty default

### Bugfixes

- release commit message

### Miscellaneous

- cleanup

## [2.0.8] - 2026-11-11

### Changes

- run spotless after clearskies expansion
- add 'zone.rong.clearskies' to expand star imports
- include changelog and author signing in automated release

### Miscellaneous

- always apply spotless last after sync

## [2.0.7] - 2026-11-11

### Changes

- refactor idea tasks
- disable maven publishing in template

### Bugfixes

- instead of hardcoding 'origin' as remote iterate through all

## [2.0.6] - 2026-11-11

### Changes

- move mustRunAfter into actual task
- apply 2.0.5 retroactively
- remove buildscript_version from property docs

### Bugfixes

- spotless implicit dependency

### Miscellaneous

- manually add changelog for 2.0.5

## [2.0.5] - 2026-11-11

### Changes

- isolated verifyRelease task
- apply spotless last
- add prepareRelease task
- add custom version handling for the README.md shield
- work on more release shenanigans
- tweak task order for release task
- add spotless to release task
- move buildscript version bump to release task

## [2.0.4] - 2026-11-11

### Changes

- work on automated changelog generation
- update build.gradle.kts to use tag based versioning
- add kotlin reference object to .gitignore
- remove Kotlin reference object
- add unreleased section to changelog
- remove trailing whitespace from prop doc string
- remove mod_version property
- add recon for tag based versioning

### Bugfixes

- hardcoded changelog path
- path for jetbrains changelog plugin
- maven url in README.md

### Miscellaneous

- reinforce markdown formatting
- hide gradle deprecation warning

## [2.0.3] - 2026-11-10

_Getting Groovy_

- add formatting for Groovy(Script) files
- fix GroovyScript jvmArg not getting applied
- README.md will now get updated with buildscripts
- template version becomes project version in template repo

## [2.0.2] - 2026-11-09

_Java Auto Import_

- disable Java star import

## [2.0.1] - 2026-11-09

_IntelliJ Formatting_

- fix auto formatting of `*.properties` files
- improve docs for `tags.properties` and `secrets.example.properties`
- set vendor and langVersion for Idea

## [2.0.0] - 2026-11-09

_The major refactor_

- rewrite all buildscripts from scratch
- remove custom plugin
- move functionality to `build.gradle.kts`
- updated RFG to 2.0.6

[Unreleased]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.4.4...HEAD
[2.4.4]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.4.3...2.4.4
[2.4.3]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.4.2...2.4.3
[2.4.2]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.4.1...2.4.2
[2.4.1]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.4.0...2.4.1
[2.4.0]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.3.4...2.4.0
[2.3.4]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.3.3...2.3.4
[2.3.3]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.3.2...2.3.3
[2.3.2]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.3.1...2.3.2
[2.3.1]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.3.0...2.3.1
[2.3.0]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.2.4...2.3.0
[2.2.4]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.2.3...2.2.4
[2.2.3]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.2.2...2.2.3
[2.2.2]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.2.1...2.2.2
[2.2.1]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.2.0...2.2.1
[2.2.0]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.1.4...2.2.0
[2.1.4]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.1.3...2.1.4
[2.1.3]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.1.2...2.1.3
[2.1.2]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.1.1...2.1.2
[2.1.1]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.1.0...2.1.1
[2.1.0]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.9...2.1.0
[2.0.9]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.8...2.0.9
[2.0.8]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.7...2.0.8
[2.0.7]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.6...2.0.7
[2.0.6]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.5...2.0.6
[2.0.5]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.4...2.0.5
[2.0.4]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.3...2.0.4
[2.0.3]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.2...2.0.3
[2.0.2]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.1...2.0.2
[2.0.1]: https://github.com/Ender-Development/EnderDevelopmentTemplate/compare/2.0.0...2.0.1
[2.0.0]: https://github.com/Ender-Development/EnderDevelopmentTemplate/commits/2.0.0
