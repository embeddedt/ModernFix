---
description: Root-cause analysis for a ModernFix GitHub issue. Use when the user wants to investigate or debug a reported issue.
disable-model-invocation: true
argument-hint: [issue-number]
arguments: issue
---

Perform a root-cause analysis for a ModernFix GitHub issue.

1. **Fetch the issue.** If no issue number was given, ask for one. Run `gh issue view $issue; gh issue view $issue --comments` to read the issue and its comments.

2. **Identify the affected version.** Look for a ModernFix version string (e.g. `5.27.10+mc1.20.1`) in the issue body or comments.

3. **Identify related mods.** If reproducing the issue requires other mods, ask the user for their Git repository links or local source paths before continuing. Do not rely on your knowledge of how the mod is implemented from training data. Mods evolve quickly. If the code is available in a Git repository, clone it to a folder in `/tmp` first before reading. You may initially want to use a shallow clone scoped to a specific branch to reduce download size. Do not use the GitHub API or awkward HTTPS requests to read source code.

4. **Regression check.** If the issue is a regression: use `python ${CLAUDE_PROJECT_DIR}/scripts/find-commit-for-version.py <version>` to find the commit associated with a given version. Run `git log --oneline <that-commit>..HEAD` to see what changed since then. Investigate those commits as the first suspects, but do not assume the regression is introduced in that range.

5. **Analyze and report.** Identify the root cause, cite specific files and line numbers, and suggest a fix or next step. Do not edit ModernFix code yourself. This skill owns triage and RCA, not patching.

Guidelines:

- Proactively read source code for Minecraft as well as the relevant mods; don't rely on implementation knowledge memorized in training data.
- Always consider the report as potentially unreliable but directionally accurate. The "minimal mod list" they provide can be wrong and not list a critical additional mod needed to reproduce. Be aware of this when analyzing code and pay attention to conditions that require a mod not mentioned in order to trigger.

Never reply to GitHub issues directly. Your job is to assist the user, not directly communicate with the issue reporter.
