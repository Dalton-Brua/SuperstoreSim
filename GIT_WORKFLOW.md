# Git Workflow — Superstore Simulator

## Branch Structure

| Branch | Purpose |
|--------|---------|
| `main` | **Production** — stable, tested code only. Never commit directly here. |
| `dev`  | **Development** — active work and testing. This is your default working branch. |

---

## Day-to-Day Workflow

### 1. Starting new work
Always branch off `dev` for a new feature or fix:
```bash
git checkout dev
git checkout -b feature/my-feature-name
```

### 2. Save progress
```bash
git add .
git commit -m "feat: describe what changed"
```

### 3. Merge finished work back into dev
```bash
git checkout dev
git merge feature/my-feature-name
git branch -d feature/my-feature-name   # clean up the feature branch
```

### 4. Promote dev → main (production release)
Only when `dev` is stable and tested on device:
```bash
git checkout main
git merge dev
git tag v1.x.x          # optional: tag the release
git checkout dev         # go back to dev immediately
```

---

## Quick Commands

```bash
# See where you are
git status
git branch

# See recent history
git log --oneline --graph --all

# Discard uncommitted changes to a file
git checkout -- path/to/file.kt

# Undo the last commit (keeps changes staged)
git reset --soft HEAD~1

# Stash work-in-progress before switching branches
git stash
git stash pop            # restore it later
```

---

## Commit Message Convention

```
feat:  new feature
fix:   bug fix
refac: refactor (no behaviour change)
test:  add or update tests
docs:  documentation only
chore: build config, dependencies, gitignore
```

Example: `feat: add bulk order dialog with volume discounts`

---

## Branch Protection (manual rules to follow)

- ❌ Never `git push --force` to `main`
- ❌ Never commit directly to `main` — always go through `dev`
- ✅ Run `./gradlew test` before merging into `main`
- ✅ Build a debug APK and test on device before promoting to `main`

