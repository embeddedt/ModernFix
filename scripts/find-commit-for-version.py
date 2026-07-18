#!/usr/bin/env python3
"""
Find the Git commit that produced a given ModernFix version string.

Usage:
    python scripts/find-commit-for-version.py 5.27.51+mc1.20.1
    python scripts/find-commit-for-version.py 5.27.51+1.20.1       (also accepted)
    python scripts/find-commit-for-version.py 5.27.51+mc1.20.1 --branch 1.20

The version scheme (from buildSrc/src/main/kotlin/GitVersionSource.kt):
    {releaseLine}.{patch}+mc{minecraftVersion}

Where:
    - releaseLine = content of release_line.txt (e.g. "5.27")
    - patch = number of first-parent commits from the last commit that touched
              release_line.txt to HEAD
    - minecraftVersion = the minecraft_version property from gradle.properties

This script reverses that mapping to find the exact commit.
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional


REPO_ROOT = Path(__file__).resolve().parent.parent


def git(*args: str, cwd: Optional[Path] = None) -> str:
    """Run a git command and return stdout (stripped)."""
    cwd = cwd or REPO_ROOT
    result = subprocess.run(
        ["git"] + list(args),
        cwd=cwd,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def git_lines(*args: str, cwd: Optional[Path] = None) -> list[str]:
    """Run git and return non-empty lines."""
    out = git(*args, cwd=cwd)
    return [l for l in out.splitlines() if l.strip()] if out else []


def parse_version(version_str: str) -> dict:
    """
    Parse a version string like '5.27.51+mc1.20.1' or '5.27.51+1.20.1'.

    Returns a dict with keys: release_line, patch, mc_version
    """
    # Match: release_line.patch+mc?minecraft_version
    m = re.match(
        r"(\d+\.\d+)\.(\d+)\+(?:mc)?(\d+\.\d+(?:\.\d+)?)$",
        version_str,
    )
    if not m:
        print(
            f"Error: cannot parse version '{version_str}'. "
            f"Expected format: X.Y.Z+mcM.N[.P]",
            file=sys.stderr,
        )
        sys.exit(1)

    return {
        "release_line": m.group(1),
        "patch": int(m.group(2)),
        "mc_version": m.group(3),
    }


def find_refs_for_mc_version(mc_version: str) -> list[str]:
    """
    Find local and remote-tracking refs whose gradle.properties contains
    minecraft_version=<mc_version>. Remote refs are returned as-is
    (e.g. 'origin/1.20').
    """
    candidates = []
    # Local branches then remote-tracking branches
    refs = git_lines("branch", "--format=%(refname:short)")
    refs += git_lines("branch", "-r", "--format=%(refname:short)")
    seen = set()
    for ref in refs:
        if not ref or ref in seen:
            continue
        seen.add(ref)
        props = git("show", f"{ref}:gradle.properties")
        if props and re.search(
            rf"^minecraft_version\s*=\s*{re.escape(mc_version)}\s*$",
            props, re.MULTILINE,
        ):
            candidates.append(ref)
    return candidates


def find_release_line_touch_commit(ref: str) -> Optional[str]:
    """
    Return the most recent first-parent commit that touched release_line.txt
    on the first-parent path from `ref`, or None if none found.
    """
    return git(
        "log", "--first-parent", "-n", "1", "--format=%H",
        ref, "--", "release_line.txt",
    ) or None


def get_release_line_at_commit(commit: str) -> Optional[str]:
    """Return the content of release_line.txt at the given commit, or None."""
    content = git("show", f"{commit}:release_line.txt")
    return content.strip() if content else None


def find_commit_by_patch_count(
    base_commit: str,
    target_patch: int,
    tip: str,
) -> Optional[str]:
    """
    Given a base commit (the last commit touching release_line.txt),
    find the commit that is exactly `target_patch` first-parent steps
    ahead of it (i.e. the HEAD that would give rev-list count = target_patch).

    This works by listing all first-parent commits from base_commit (exclusive)
    to the branch tip, in oldest-first order, and picking the target_patch-th one.

    Returns the commit hash, or None if not enough commits exist or the
    objects aren't in the same DAG.
    """
    revs = git_lines(
        "rev-list", "--reverse", "--first-parent",
        f"{base_commit}..{tip}",
    )

    if not revs:
        return None

    if target_patch < 1:
        return base_commit  # patch=0 means base itself

    if target_patch > len(revs):
        return None

    return revs[target_patch - 1]


def count_patches(commit: str) -> Optional[int]:
    """
    Compute the patch count for a given commit, as GitVersionSource would.
    Returns the number of first-parent commits from the last release_line.txt
    touch to the given commit.
    """
    line_start = find_release_line_touch_commit(commit)
    if not line_start:
        return None
    count_str = git("rev-list", "--count", "--first-parent", f"{line_start}..{commit}")
    try:
        return int(count_str) if count_str else None
    except ValueError:
        return None


def try_match_version_on_branch(
    version_info: dict,
    branch: str,
) -> Optional[str]:
    """
    Try to find the commit that produced the given version on the given branch.
    Returns the commit hash or None.
    """
    rel = version_info["release_line"]
    patch = version_info["patch"]

    print(f"  Searching branch '{branch}'...")

    # 1. Find the most recent first-parent commit touching release_line.txt
    touch_commit = find_release_line_touch_commit(branch)
    if not touch_commit:
        print(f"    -> No commits touching release_line.txt found on this branch")
        return None

    touch_content = get_release_line_at_commit(touch_commit)
    print(f"    -> Last release_line.txt touch: {touch_commit[:12]} (content: '{touch_content}')")

    if touch_content != rel:
        # The current release line differs from what we're looking for.
        print(f"    -> Current release line is '{touch_content}', searching for '{rel}'...")

        # Get all first-parent commits that touched release_line.txt, newest first
        all_touches = git_lines(
            "log", "--first-parent", "--format=%H",
            branch, "--", "release_line.txt",
        )

        found = None
        next_touch = None
        for i, tc in enumerate(all_touches):
            tc_content = get_release_line_at_commit(tc)
            if tc_content == rel:
                found = tc
                if i > 0:
                    next_touch = all_touches[i - 1]
                break

        if not found:
            print(f"    -> Could not find any commit with release_line.txt = '{rel}'")
            return None

        touch_commit = found
        if next_touch:
            print(f"    -> '{rel}' set at {found[:12]}, changed away at {next_touch[:12]}")
        else:
            print(f"    -> '{rel}' set at {found[:12]} (still current)")

    # 2. Find the patch-th first-parent descendant of touch_commit
    target = find_commit_by_patch_count(touch_commit, patch, branch)
    if not target:
        rev_count = len(git_lines("rev-list", "--reverse", "--first-parent", f"{touch_commit}..{branch}"))
        print(f"    -> Patch count {patch} exceeds available commits ({rev_count}) on this branch")
        return None

    print(f"    -> Candidate: {target}")

    # 3. Verify
    # Use the candidate itself as the ref for touch-commit lookup
    actual_patch = count_patches(target)
    if actual_patch == patch:
        print(f"    -> VERIFIED ✓")
        return target
    else:
        print(f"    -> Patch count mismatch: found {actual_patch}, expected {patch}")
        return None


def main():
    parser = argparse.ArgumentParser(
        description="Find the git commit for a ModernFix version string",
    )
    parser.add_argument(
        "version",
        help="Version string, e.g. '5.27.51+mc1.20.1' or '5.27.51+1.20.1'",
    )
    parser.add_argument(
        "--branch",
        help="Branch to search (default: auto-detect from MC version)",
        default=None,
    )

    args = parser.parse_args()

    version_info = parse_version(args.version)
    print(f"Version: {args.version}")
    print(f"  Release line: {version_info['release_line']}")
    print(f"  Patch count:  {version_info['patch']}")
    print(f"  MC version:   {version_info['mc_version']}")
    print()

    # First, try to find a matching tag
    tag_out = git("rev-parse", "--verify", "--quiet", args.version)
    if tag_out:
        print(f"Found exact tag: {args.version} -> {tag_out}")
        print(f"\nResult: {tag_out}")
        return

    # Determine which refs to search
    if args.branch:
        refs = [args.branch]
    else:
        refs = find_refs_for_mc_version(version_info["mc_version"])
        if not refs:
            print(
                f"No branch found with minecraft_version={version_info['mc_version']}.\n"
                f"Fetch remote branches with: git fetch origin\n"
                f"Available refs:",
                file=sys.stderr,
            )
            for b in git_lines("branch", "--format=%(refname:short)"):
                print(f"  {b}", file=sys.stderr)
            for b in git_lines("branch", "-r", "--format=%(refname:short)"):
                print(f"  {b}", file=sys.stderr)
            print("\nUse --branch <name> to search a specific ref.", file=sys.stderr)
            sys.exit(1)

    result = None
    for ref in refs:
        result = try_match_version_on_branch(version_info, ref)
        if result:
            print()
            break

    if result:
        print(f"Result: {result}")
    else:
        print(f"\nCould not find commit for version {args.version}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
