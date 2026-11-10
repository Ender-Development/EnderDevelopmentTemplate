# Ender-Development.org Template Changelog

<!--
Writing a changelog is a good practice for maintaining a clear history of changes in your project.
Try to follow the [Common Changelog](https://common-changelog.org/) conventions.
-->

## [Unreleased]

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

