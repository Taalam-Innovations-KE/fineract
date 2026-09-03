# Taalam Development Policy

This repository is a Taalam-managed fork of Apache Fineract. Upstream Apache
source remains under Apache 2.0. Taalam code under `custom/taalam/**` is
proprietary and must use the Taalam proprietary header.

## Repository Visibility

GitHub does not allow public forks to be converted directly to private
repositories. To make this codebase private, create a standalone private
repository under the Taalam organization and push the current branch/history to
that repository. Keep this fork for upstream synchronization only, or remove the
proprietary `custom/taalam/**` tree from the public fork after the private
repository is ready.

## GitHub Actions

Normal pull requests and `develop` pushes run the fast CI tier:

- `build-core`
- quality checks: Spotless, RAT, Checkstyle, SpotBugs, and Javadoc

The full regression tier runs on `workflow_dispatch`, nightly schedule, and
release branches:

- Docker image checks
- documentation and progressive loan builds
- Cucumber tests
- PostgreSQL, MySQL, and MariaDB integration matrices
- E2E tests
- messaging smoke tests
- Liquibase/API compatibility checks
- database regression safety checks

Private repository CI is disabled unless the repository variable
`ENABLE_PRIVATE_REPOSITORY_CI` is set to `true`.

Configure optional external jobs with these repository variables:

- `ENABLE_SONARQUBE=true`
- `SONAR_ORGANIZATION`
- `SONAR_PROJECT_KEY`
- `SONAR_HOST_URL`
- `ENABLE_DOCKERHUB_PUBLISH=true`
- `DOCKER_IMAGE`, for example `taalamke/taalam-fincore`

Configure these repository secrets:

- `SONARCLOUD_TOKEN`
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DEVELOCITY_ACCESS_KEY`, optional

Only enable DockerHub publishing after the target image repository is private.

## Build Customisation

Every Gradle change this fork needs is expressed in files the fork owns, so that
upstream build files stay byte-identical to `apache/fineract` and merges from
upstream do not conflict on them.

There are exactly three places fork build logic may live:

| File | Scope | Owned by |
| --- | --- | --- |
| `custom/taalam/<category>/<module>/build.gradle` | one module | fork |
| `custom/taalam/gradle/taalam-module.gradle` | all Taalam modules | fork |
| `custom/taalam/gradle/taalam-root.gradle` | reconfigures upstream projects | fork |

The only edit to an upstream Gradle file is a 13-line hook at the very bottom of
the root `build.gradle`:

```gradle
def taalamRootOverrides = file("$rootDir/custom/taalam/gradle/taalam-root.gradle")
if (taalamRootOverrides.exists()) {
    apply from: taalamRootOverrides
}
```

Keep that block last in the file and add nothing above it.

### Choosing where a change goes

1. Can the module configure it itself? Put it in
   `custom/taalam/gradle/taalam-module.gradle`. This runs after the root
   `allprojects { ... }` block, so it overrides upstream defaults. The
   proprietary license header and the RAT exclusion work this way.
2. Is it a dependency-scope problem? Fix it in the module's
   `dependencies.gradle`. Fineract platform projects are declared `compileOnly`
   plus `testImplementation`, never `implementation`: `fineract-provider` *is*
   the application, so a custom module that ships it transitively puts a second
   copy of `fineract-core` and the `fineract-provider` `-plain` jar on the
   runtime classpath. That duplicates `application.properties`, which breaks
   property binding (`fineract.mode` to `NullPointerException`) and Liquibase
   changelog lookup. Keeping these off `runtimeElements` is what allows
   `custom/docker/build.gradle` to stay untouched.
3. Does it have to reconfigure an upstream project? Put it in
   `custom/taalam/gradle/taalam-root.gradle`. It currently handles the root RAT
   excludes, the `bootRun` classpath for custom modules, and keeping the
   upstream `acme` demo modules out of the custom Docker image.

Note that `taalam-root.gradle` is applied during root project evaluation, before
any subproject is evaluated. Anything touching a task or configuration that a
subproject's own `build.gradle` creates must be deferred with `afterEvaluate` or
a lazy `configureEach`.

### Merge hygiene guard

```bash
./gradlew taalamUpstreamFootprint -PtaalamUpstreamRef=upstream/develop
```

This fails when the fork has modified an upstream file that is not on the
accepted list in `custom/taalam/gradle/taalam-root.gradle`. Adding an entry is
allowed, but do it in the same commit as the change so the growth is visible in
review. Anything on that list is a file that can conflict on the next merge, or
worse, merge cleanly and break the compile.

## Upstream Synchronisation

Add the upstream remote once:

```bash
git remote add upstream https://github.com/apache/fineract.git
```

Sync every one to two weeks. Drift is the dominant cost: a fortnight is roughly
50 commits and a few minutes of work, while a quarter is 700 or more commits and
a project. Merging through the GitHub "Sync fork" button is what produces the
`Merge branch 'apache:develop' into develop` commits in this history; it gives
no way to preview a merge, cherry-pick, or batch the work, so use the remote.

```bash
git fetch upstream develop
git merge-tree --write-tree --name-only HEAD upstream/develop   # preview conflicts
git merge upstream/develop
```

A clean merge is not a working merge. `custom/taalam` compiles against roughly
60 upstream classes, and upstream can change any of them without producing a
conflict, so always finish with:

```bash
./gradlew taalamUpstreamFootprint -PtaalamUpstreamRef=upstream/develop
./gradlew :custom:taalam:tenant:runtime:test :custom:taalam:security:keycloak:test
```

## Git And Jira

Use conventional commit style and include the Jira work item key when work maps
to your personal Jira board.

Recommended format:

```text
feat(auth): ABC-123 add keycloak user provisioning
fix(ci): ABC-124 skip sonar unless configured
chore(build): ABC-125 align private fork CI policy
```

Recommended branch format:

```text
ABC-123-feat-keycloak-user-provisioning
```

Recommended pull request title:

```text
feat(auth): ABC-123 add keycloak user provisioning
```

To connect GitHub and Jira:

1. In Jira Cloud, install the GitHub for Atlassian app.
2. Connect the GitHub organization that owns this repository.
3. Grant the app access to this repository.
4. Confirm Smart Commits are enabled for the repository in Jira app settings.
5. Include the Jira key in branch names, commit messages, and pull request
   titles so Jira can attach development activity to the work item.

References:

- https://support.atlassian.com/jira-cloud-administration/docs/integrate-with-github/
- https://support.atlassian.com/jira-cloud-administration/docs/enable-smart-commits/
