# CI/CD Pipeline Documentation - Otter

**Purpose**: Otter-specific CI/CD configuration. Generic pipeline logic lives in [condor](https://github.com/TomasGC/condor).
**Last Updated**: 2026-06-21

---

## Overview

Otter uses 3 thin wrapper workflows that delegate all logic to [TomasGC/condor](https://github.com/TomasGC/condor):

| Workflow | Trigger | Delegates to |
|----------|---------|--------------|
| `push-ci.yml` | Push to `feature/**` or `bugfix/**` | `condor/kotlin/push-ci.yml` + `condor/python/push-ci.yml` |
| `pr-ci.yml` | `workflow_run` (after Push-CI) | `condor/common/pr-ci.yml` |
| `cd.yml` | Push to `v*` tags | `condor/kotlin/cd.yml` |

For full pipeline architecture, workflow inputs/outputs, and individual reusable workflows, see **[condor README](https://github.com/TomasGC/condor#readme)**.

---

## Otter-Specific Inputs

### push-ci.yml

```yaml
kotlin-pipeline:
  uses: TomasGC/condor/.github/workflows/kotlin/push-ci.yml@main
  with:
    pre-test-command: python scripts/manage.py create archives --rpa-only
    archives-local-dir: archives/
    device-archives-path: /sdcard/otter
    app-name: otter
```

- `pre-test-command`: generates RPA test archive before each JVM test job (unit, integration-mock, integration-real, coverage)
- `archives-local-dir` + `device-archives-path`: pushes generated archives to emulator after boot, before instrumented tests run

```yaml
python-pipeline:
  uses: TomasGC/condor/.github/workflows/python/push-ci.yml@main
  with:
    skip-on-no-changes: true
```

- `skip-on-no-changes: true`: Python pipeline runs only when `scripts/**/*.py` files changed

### cd.yml

```yaml
release:
  uses: TomasGC/condor/.github/workflows/kotlin/cd.yml@main
  with:
    app-name: otter
```

- `app-name: otter`: release APK named `otter-X.Y.Z.apk`

**Tag convention**: `v1.0.0` = stable release, `v1.0.0-alpha` / `v1.0.0-rc1` = pre-release (any hyphen suffix)

### pr-ci.yml

```yaml
pr-checks:
  uses: TomasGC/condor/.github/workflows/common/pr-ci.yml@main
  with:
    push-ci-conclusion: ${{ github.event.workflow_run.conclusion }}
    head-branch: ${{ github.event.workflow_run.head_branch }}
    head-sha: ${{ github.event.workflow_run.head_sha }}
```

Passes `workflow_run` context to condor (lost inside `workflow_call` boundaries).

---

## Required Secrets

| Secret | Used for |
|--------|---------|
| `KEYSTORE_PASSWORD` | Release APK signing |
| `KEY_PASSWORD` | Release APK signing |
| `GIST_SECRET` | Coverage badge update |
| `NVD_API_KEY` | OWASP dependency check |

---

## Test Archive Generation

CI generates RPA archives on-the-fly (committed `archives/` is gitignored):

1. `pre-test-command: python scripts/manage.py create archives --rpa-only` — runs before each JVM test job
2. Instrumented tests: condor pushes `archives/` to `/sdcard/otter` after emulator boots

Local generation:
```bash
# RPA only (pure Python, no external deps)
python scripts/manage.py create archives --rpa-only

# All formats (requires 7-Zip + Docker for RAR)
python scripts/manage.py create archives
```

---

## Coverage Thresholds

| Pipeline | Tool | Threshold |
|----------|------|-----------|
| Kotlin | Kover | ≥ 80% |
| Python | pytest-cov | ≥ 80% |

---

## Local Development Commands

See `.claude/contexts/commands.md` for full reference.

```bash
python scripts/manage.py build --no-install  # Build only
python scripts/manage.py test unit           # JVM tests
python scripts/manage.py test instrumented   # Requires device
python scripts/manage.py test coverage       # With Kover report
```
