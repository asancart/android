#!/usr/bin/env python3
"""Commit policy gate (T0.3): every commit is signed and carries no AI attribution.

Signed means GitHub verified the signature (`commit.verification.verified`), so SSH and GPG
signatures both count. Checks the commits a push or pull request brings, and for a pull request
also its title and body. Stdlib only; the same file is in backend, frontend and android
(`.github/scripts/commit_policy.py`), keep them identical.

Env (set by GitHub Actions): GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_EVENT_NAME,
GITHUB_EVENT_PATH, GITHUB_SHA, GITHUB_API_URL, GITHUB_STEP_SUMMARY (optional).
"""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request

ZERO_SHA = "0" * 40

# Names of AI tools and vendors. Word-bounded so "Kai" or "maintain" don't match.
_AI = (
    r"(?:claude|anthropic|openai|chat\s*gpt|gpt-?\d\w*|copilot|gemini|codex|cursor\s*agent|devin|aider|"
    r"tabnine|codeium|windsurf|codewhisperer|amazon\s+q|llms?|ai)"
)
_TRAILER_KEYS = (
    r"(?:co-authored-by|signed-off-by|assisted-by|generated-by|helped-by|reviewed-by|made-with|"
    r"suggested-by|written-by)"
)
MARKERS: list[tuple[str, re.Pattern[str]]] = [
    ("AI attribution trailer", re.compile(rf"^\s*{_TRAILER_KEYS}\s*:.*\b{_AI}\b", re.I | re.M)),
    ("AI-assisted trailer", re.compile(r"^\s*ai-(?:assisted|generated|agent)\s*:", re.I | re.M)),
    ("'generated with' an AI tool", re.compile(
        rf"\b(?:generated|created|written|made)\s+(?:with|by|using)\s+[\[(]?\s*(?:an?\s+)?{_AI}\b", re.I)),
    ("robot emoji", re.compile("\U0001F916")),
    ("AI vendor address or link", re.compile(
        r"noreply@anthropic\.com|@openai\.com|@cursor\.com|claude\.com/claude-code|claude\.ai/code", re.I)),
]


def ai_markers(text: str | None) -> list[str]:
    """Names of the AI attribution markers found in a commit message or PR text."""
    if not text:
        return []
    return [name for name, pattern in MARKERS if pattern.search(text)]


def check_commit(c: dict) -> list[str]:
    """Problems with one commit from the GitHub REST API (compare or pulls/commits shape)."""
    problems = []
    verification = (c.get("commit") or {}).get("verification") or {}
    if verification.get("verified") is not True:
        problems.append(f"not signed or not verified by GitHub (reason: {verification.get('reason', 'unknown')})")
    problems += [f"AI attribution: {m}" for m in ai_markers((c.get("commit") or {}).get("message"))]
    return problems


class Api:
    def __init__(self, base: str, repo: str, token: str):
        self.base, self.repo, self.token = base.rstrip("/"), repo, token

    def get(self, path: str) -> object:
        req = urllib.request.Request(f"{self.base}/repos/{self.repo}/{path}", headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {self.token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "commit-policy",
        })
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)


def compare(api, base: str, head: str) -> list[dict]:
    """Commits reachable from head but not from base (all pages)."""
    out: list[dict] = []
    page = 1
    while True:
        data = api.get(f"compare/{base}...{head}?per_page=100&page={page}")
        batch = data.get("commits") or []
        out += batch
        if not batch or len(out) >= data.get("total_commits", 0):
            return out
        page += 1


def pr_commits(api, number: int) -> list[dict]:
    out: list[dict] = []
    page = 1
    while True:
        batch = api.get(f"pulls/{number}/commits?per_page=100&page={page}")
        out += batch
        if len(batch) < 100:
            return out
        page += 1


def history(api, head: str, limit: int = 1000) -> list[dict]:
    out: list[dict] = []
    page = 1
    while len(out) < limit:
        batch = api.get(f"commits?sha={head}&per_page=100&page={page}")
        out += batch
        if len(batch) < 100:
            break
        page += 1
    return out


def collect(api, event_name: str, event: dict, sha: str) -> tuple[list[dict], list[tuple[str, str]], str]:
    """(commits, [(label, text)] for PR title/body, description of the range)."""
    default = (event.get("repository") or {}).get("default_branch") or "main"
    if event_name in ("pull_request", "pull_request_target"):
        pr = event["pull_request"]
        texts = [("PR title", pr.get("title") or ""), ("PR body", pr.get("body") or "")]
        return pr_commits(api, pr["number"]), texts, f"pull request #{pr['number']}"
    if event_name == "push":
        if event.get("deleted"):
            return [], [], "branch deleted"
        after, before, ref = event["after"], event.get("before") or ZERO_SHA, event.get("ref", "")
        if before != ZERO_SHA:
            try:
                return compare(api, before, after), [], f"{before[:7]}..{after[:7]}"
            except urllib.error.HTTPError as e:
                if e.code != 404:
                    raise
                # before is gone (e.g. rewritten history): fall back to the default branch
        if ref == f"refs/heads/{default}":
            return history(api, after), [], f"history of {default} at {after[:7]}"
        return compare(api, default, after), [], f"{default}...{after[:7]}"
    # workflow_dispatch and anything else: the branch against the default branch
    return compare(api, default, sha), [], f"{default}...{sha[:7]}"


def main() -> int:
    env = os.environ
    api = Api(env.get("GITHUB_API_URL", "https://api.github.com"), env["GITHUB_REPOSITORY"], env["GITHUB_TOKEN"])
    with open(env["GITHUB_EVENT_PATH"], encoding="utf-8") as f:
        event = json.load(f)
    commits, texts, what = collect(api, env["GITHUB_EVENT_NAME"], event, env.get("GITHUB_SHA", ""))

    lines, failures = [], 0
    for c in commits:
        subject = ((c.get("commit") or {}).get("message") or "").splitlines()[0:1] or [""]
        problems = check_commit(c)
        failures += bool(problems)
        status = "FAIL" if problems else "ok  "
        lines.append(f"{status} {c['sha'][:7]} {subject[0][:72]}")
        lines += [f"       - {p}" for p in problems]
    for label, text in texts:
        found = ai_markers(text)
        failures += bool(found)
        lines.append(f"{'FAIL' if found else 'ok  '} {label}")
        lines += [f"       - AI attribution: {m}" for m in found]

    verdict = (f"commit policy: {failures} problem(s)" if failures
               else f"commit policy passed: {len(commits)} commit(s) signed, no AI attribution")
    report = "\n".join([f"Checked {what}: {len(commits)} commit(s)", *lines, verdict])
    print(report)
    if env.get("GITHUB_STEP_SUMMARY"):
        with open(env["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(f"### Commit policy\n\n```\n{report}\n```\n")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
