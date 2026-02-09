# Android build and release flow: comparison with aepsdk-edge-android

This document compares **aca-mobile-sdk-android-extension** with [adobe/aepsdk-edge-android](https://github.com/adobe/aepsdk-edge-android) (focus on [code/](https://github.com/adobe/aepsdk-edge-android/tree/main/code)) for build layout, Makefile, and release workflow.

---

## 1. Project layout

| Aspect | aepsdk-edge-android | aca-mobile-sdk-android-extension |
|--------|----------------------|----------------------------------|
| **Gradle root** | Under `code/` | Under `code/` |
| **Config files** | `code/build.gradle.kts`, `code/settings.gradle.kts`, `code/gradle.properties`, `code/gradlew` | Same (under `code/`) |
| **Extension module** | `code/edge/` | `code/contentanalytics/` |
| **Sample apps** | `code/app`, `code/app-kotlin`, `code/app-util-xdm` | `code/sample-app/` |
| **Extra modules** | `code/upstream-integration-tests` | — |

**Takeaway:** Both use a **`code/`** directory as the Gradle project root. Paths in Makefile and workflows are under `code/` for both.

---

## 2. Makefile

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **Gradle invocations** | `./code/gradlew -p code <task>` (run from repo root) | Same |
| **Extension module** | `edge` | `contentanalytics` |
| **Test app(s)** | `app`, `app-kotlin` (separate targets) | `sample-app` (single) |
| **clean** | `./code/gradlew -p code clean` | Same |
| **ci-publish** | `./code/gradlew -p code/edge publishReleasePublicationToSonatypeRepository -Prelease` | `./code/gradlew -p code contentanalytics:publish -Prelease` |
| **ci-publish-maven-local-jitpack** | `... publishReleasePublicationToMavenLocal -Pjitpack **-x signReleasePublication**` | `... publishReleasePublicationToMavenLocal -Pjitpack` (no `-x signReleasePublication`) |
| **ci-publish-staging** | `publishReleasePublicationToSonatypeRepository` (no clean) | `publish` (with `clean`) |
| **Lint / format** | `lint: checkformat` then spotlessCheck + lint; format runs spotlessApply on edge + both apps | Lint only; format runs spotlessApply on contentanalytics (spotless disabled in module) |
| **Integration tests** | `upstream-integration-test`, `integration-test-coverage` | — |

**Takeaway:** Edge uses an **explicit Sonatype publish task** and **skips signing** for local/JitPack publish (`-x signReleasePublication`). We use the generic `publish` task (likely wired by aep-library to a staging directory for JReleaser in 3.4.3) and do not skip signing for local publish.

---

## 3. Root build.gradle.kts

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **Buildscript** | Only `aepsdk-commons` (and aep-license); no AGP/Kotlin in root | AGP, Kotlin, aepsdk-commons |
| **Repositories** | In buildscript only (gradlePluginPortal, google, mavenCentral, jitpack, mavenLocal) | buildscript + allprojects (google, mavenCentral, gradlePluginPortal, jitpack, maven adobe) |
| **Extra** | `apply(plugin = "aep-license")` | `tasks.register("clean", Delete::class)` |

**Takeaway:** Edge keeps the root buildscript minimal; we declare AGP and Kotlin at root. Both rely on aep-library in the extension module for most configuration.

---

## 4. settings.gradle.kts

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **rootProject.name** | `aepsdk-edge-android` | `aca-mobile-sdk-android-extension` |
| **Includes** | `:edge`, `:app`, `:app-kotlin`, `:app-util-xdm`, `:upstream-integration-tests` | `:contentanalytics`, `:sample-app` |
| **dependencyResolutionManagement** | Yes (repos mode FAIL_ON_PROJECT_REPOS, sonatype snapshots, jitpack, mavenLocal) | No (repos in allprojects in root build) |

**Takeaway:** Edge uses `dependencyResolutionManagement`; we use `allprojects` for repos. Both are valid; edge’s approach is the modern pattern.

---

## 5. gradle.properties

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **moduleName** | `edge` | `contentanalytics` |
| **moduleVersion** | `3.0.2` | `3.0.0` |
| **Dependency versions** | Pinned: mavenCoreVersion=3.3.0, mavenEdgeIdentityVersion=3.0.1, etc. | Dynamic: mavenCoreVersion=3.+, mavenEdgeVersion=3.+, mavenEdgeIdentityVersion=3.+ |
| **mavenRepoName / mavenRepoDescription** | Set | Set |
| **Extra** | mavenUploadDryRunFlag, EDGE_LOCATION_HINT, TAGS_MOBILE_PROPERTY_ID for integration tests | org.gradle.jvmargs, AndroidX, Kotlin code style |

**Takeaway:** Edge pins dependency versions (validated in release); we use dynamic versions (3.+). Release workflow can still validate Core (and optionally others) via version-validation-dependencies.

---

## 6. Release workflow (maven-release.yml)

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **Commons ref** | `gha-android-3.3.1` | `gha-android-3.4.3` |
| **version-validation-paths** | `code/gradle.properties`, `code/edge/src/main/java/.../EdgeConstants.java` | `code/gradle.properties`, `code/contentanalytics/src/main/java/.../ContentAnalyticsConstants.kt` |
| **version-validation-dependencies** | Core (required), EdgeIdentity (required), from inputs | Core (optional, from input) |
| **staging-dir** | Not passed (not required in 3.3.1) | `code/contentanalytics/build/staging-deploy` |
| **Workflow inputs** | tag, create-github-release, **core-dependency** (required), **edge-identity-dependency** (required) | tag, create-github-release, **core-dependency** (optional, default '') |

**Takeaway:** We use a **newer** commons workflow that expects **staging-dir** and uses JReleaser for deploy; edge uses an older one that likely publishes from Gradle directly to Sonatype. Paths are under `code/` for both.

---

## 7. Extension module (edge vs contentanalytics)

| Aspect | Edge | Content Analytics |
|--------|------|-------------------|
| **Plugin** | `aep-library` | `aep-library` |
| **enableSpotless** | true (+ enableSpotlessPrettierForJava) | false |
| **enableDokkaDoc** | true | true |
| **Publishing** | addCoreDependency, addEdgeIdentityDependency | addCoreDependency, addEdgeDependency, addEdgeIdentityDependency |
| **consumer-rules.pro** | Not present (or from aep-library) | Not used (aligned). |
| **Source layout** | `src/main/kotlin/` | `src/main/kotlin/` (aligned). |

**Takeaway:** Same plugin; no consumer-rules.pro or DuplicatesStrategy. Edge has Spotless enabled; we have it disabled. The sources JAR task may log duplicate-path warnings (aep-library); Concierge has the same; build still succeeds.

---

## 8. Recommendation: align layout with Edge

**Decision: use `code/` layout** (Gradle under `code/`; same as Edge). Refactor completed. Align everything else with Edge where it makes sense.

### 8.1 Align with Edge (done or optional)

| Item | Action | Status |
|------|--------|--------|
| Skip signing for local/JitPack publish | Use `-x signReleasePublication` on `ci-publish-maven-local-jitpack`. | **Done** (Makefile). |
| Release secrets and GPG | Same secret names as commons; document in CONTRIBUTING. | **Done** (CONTRIBUTING.md). |
| Lint = format check + lint | Optionally make `lint` depend on spotlessCheck like Edge. | Optional (only if Spotless enabled). |
| ci-publish-staging | Edge runs Sonatype task without clean; we run `publish` with clean. | Optional: keep our clean. |

### 8.2 Keep as-is (intentional differences)

| Item | Why |
|------|-----|
| Commons 3.4.3 and staging-dir | We use newer flow (JReleaser + staging). Edge uses 3.3.1. Keep 3.4.3 and `staging-dir: code/contentanalytics/build/staging-deploy`. |
| Generic `publish` for ci-publish | aep-library wires `publish -Prelease` to staging for JReleaser. Do not switch to Sonatype task. |
| Single sample app | One sample-app is enough. |
| Optional core-dependency in release | Dynamic versions (3.+); optional Core validation is enough. |
| Spotless disabled | Enabling would require formatting; optional. |

### 8.3 Optional follow-ups

- **dependencyResolutionManagement** in `code/settings.gradle.kts`: use Edge's pattern when touching settings.
- **Root build.gradle.kts** (`code/build.gradle.kts`): could be slimmed to only aepsdk-commons; low priority.
- **GPG in CI:** if release fails with "Inappropriate ioctl for device", the fix is in aepsdk-commons (pass GPG_PASSPHRASE into the step that runs `make ci-publish`).

## References

- [aepsdk-edge-android/code](https://github.com/adobe/aepsdk-edge-android/tree/main/code)
- [aepsdk-commons Android Maven release](https://github.com/adobe/aepsdk-commons/blob/gha-android-3.4.3/.github/workflows/android-maven-release.yml) (gha-android-3.4.3)
