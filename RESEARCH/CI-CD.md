# CI/CD Setup Research — Smart Guitar Auto-Tuner

## Overview

This document covers the CI/CD configuration decisions for a multi-stack project: Spring Boot (Java 21) backend, React/Vite frontend, MySQL database, and hardware components (Arduino, Python motor control).

---

## Platform Choice: GitHub Actions

GitHub Actions was chosen because:
- The project is already hosted on GitHub
- Free tier covers public repos and reasonable private repo usage
- Native integration with pull requests and branch protection
- No external CI server needed (no Jenkins, CircleCI, etc.)

---

## Pipeline Design

### Trigger Strategy

| Event | Pipeline |
|-------|----------|
| Push to any branch | `ci.yml` — build + lint + test |
| Pull request to `main` | `ci.yml` — acts as a gate before merge |
| Merge to `main` | `docker.yml` — build image + deploy |

This ensures broken code cannot reach `main` and every successful merge produces a deployable artifact.

### Jobs

**`ci.yml` — Two parallel jobs:**

1. **Backend** — sets up Java 21 (Temurin), runs `./gradlew test bootJar --no-daemon`
2. **Frontend** — sets up Node 20, runs `npm ci && npm run lint && npm run build`

Running them in parallel reduces total CI time.

**`docker.yml` — Sequential steps:**

1. Log in to Docker Hub using repository secrets
2. Build the multi-stage `Backend/Dockerfile` and push as `autotuner-backend:latest`
3. SSH into the deployment server and run `docker compose up -d`

---

## Secrets Required

Secrets are stored in GitHub → **Settings → Secrets and variables → Actions** and never appear in logs.

| Secret | Purpose |
|--------|---------|
| `DOCKERHUB_USERNAME` | Docker Hub login |
| `DOCKERHUB_TOKEN` | Docker Hub access token (not password) |
| `SERVER_HOST` | IP or hostname of deployment server |
| `SERVER_USER` | SSH username on server |
| `SERVER_SSH_KEY` | Private SSH key (server must have the public key in `authorized_keys`) |

---

## Hardware Considerations

This project interfaces with physical hardware (audio via `/dev/snd`, GPIO via `/dev/gpiomem`). Standard cloud runners cannot test hardware-dependent code. Two mitigations:

1. **Unit test isolation** — keep hardware I/O behind interfaces so core logic can be tested without devices present.
2. **Self-hosted runner** — register a Raspberry Pi or local machine as a GitHub Actions runner. Change `runs-on: ubuntu-latest` to `runs-on: self-hosted` in the workflow to run tests directly on hardware.

---

## Branch Protection (Recommended)

Configure in GitHub → **Settings → Branches → Add rule** for `main`:

- Require status checks to pass before merging: `Backend Build & Test`, `Frontend Build & Lint`
- Require at least 1 approving review
- Dismiss stale reviews on new commits

This prevents untested or unreviewed code from reaching the deployment branch.

---

## Deployment Model

The current deployment uses Docker Compose (`database/docker-compose.yml`) which orchestrates:
- `mysql:8.0` — database with persistent volume
- `backend` — Spring Boot app built from `Backend/Dockerfile`

On each merge to `main`, the CI pipeline pulls the latest image and restarts containers via SSH. The frontend is a static build (`npm run build`) and should be served separately (e.g., Nginx, or bundled into the backend's static resources).

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [docker/build-push-action](https://github.com/docker/build-push-action)
- [appleboy/ssh-action](https://github.com/appleboy/ssh-action)
- [Self-hosted runners](https://docs.github.com/en/actions/hosting-your-own-runners)
