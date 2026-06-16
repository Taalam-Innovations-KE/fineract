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
