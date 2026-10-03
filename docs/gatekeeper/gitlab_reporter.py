#!/usr/bin/env python3
"""
GitLab Reporter for AI Gatekeeper CI/CD.
Posts the Gatekeeper review report (report.md) as a note on the active GitLab Merge Request
and updates the commit status (success/failed) via GitLab REST API.
Compatible with gitlab.unicamp.br and GitLab Cloud.
"""

import os
import sys
import json
import argparse
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path


def make_gitlab_request(url: str, method: str = "GET", data: dict = None, token: str = "") -> dict:
    headers = {
        "PRIVATE-TOKEN": token,
        "Content-Type": "application/json; charset=utf-8",
        "User-Agent": "AI-Gatekeeper-Reporter/1.0"
    }

    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read().decode("utf-8")
            return json.loads(content) if content.strip() else {}
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8") if e.fp else str(e)
        print(f"[ERROR] GitLab API request failed ({e.code} {e.reason}) to {url}: {err_msg}")
        return {"error": e.code, "message": err_msg}
    except Exception as e:
        print(f"[ERROR] Failed to connect to GitLab API {url}: {e}")
        return {"error": "connection_error", "message": str(e)}


def find_active_mr(gitlab_url: str, project_id: str, branch: str, token: str) -> dict:
    """Finds open Merge Request matching the source branch."""
    encoded_proj = urllib.parse.quote(str(project_id), safe="")
    encoded_branch = urllib.parse.quote(branch, safe="")
    url = f"{gitlab_url.rstrip('/')}/api/v4/projects/{encoded_proj}/merge_requests?state=opened&source_branch={encoded_branch}"
    
    result = make_gitlab_request(url, method="GET", token=token)
    if isinstance(result, list) and len(result) > 0:
        return result[0]
    return {}


def post_mr_note(gitlab_url: str, project_id: str, mr_iid: int, note_body: str, token: str) -> bool:
    """Posts a comment on the GitLab Merge Request."""
    encoded_proj = urllib.parse.quote(str(project_id), safe="")
    url = f"{gitlab_url.rstrip('/')}/api/v4/projects/{encoded_proj}/merge_requests/{mr_iid}/notes"
    payload = {"body": note_body}

    res = make_gitlab_request(url, method="POST", data=payload, token=token)
    return "id" in res


def update_commit_status(gitlab_url: str, project_id: str, sha: str, state: str, description: str, target_url: str, token: str) -> bool:
    """Updates commit build status on GitLab (pending, success, failed)."""
    encoded_proj = urllib.parse.quote(str(project_id), safe="")
    url = f"{gitlab_url.rstrip('/')}/api/v4/projects/{encoded_proj}/statuses/{sha}"
    payload = {
        "state": state,
        "name": "ai-gatekeeper/review",
        "description": description[:255],
        "target_url": target_url or ""
    }

    res = make_gitlab_request(url, method="POST", data=payload, token=token)
    return "id" in res or "status" in res


def main():
    parser = argparse.ArgumentParser(description="Report AI Gatekeeper results back to GitLab MR.")
    parser.add_argument("--report", default="report.md", help="Path to report.md file")
    parser.add_argument("--gitlab-url", default=os.getenv("GITLAB_URL", "https://gitlab.unicamp.br"), help="GitLab base URL")
    parser.add_argument("--project-id", default=os.getenv("GITLAB_PROJECT_ID", ""), help="GitLab Project ID or URL-encoded path")
    parser.add_argument("--token", default=os.getenv("GITLAB_TOKEN", ""), help="GitLab Personal/Project Access Token")
    parser.add_argument("--sha", default=os.getenv("GITHUB_SHA", ""), help="Commit SHA")
    parser.add_argument("--branch", default=os.getenv("GITHUB_REF_NAME", ""), help="Git source branch")
    parser.add_argument("--target-url", default=os.getenv("GITHUB_RUN_URL", ""), help="URL of CI build / run")
    parser.add_argument("--dry-run", action="store_true", help="Print report payload without posting to API")

    args = parser.parse_args()

    report_path = Path(args.report)
    if not report_path.exists():
        print(f"[WARN] Report file not found at {report_path}. Nothing to report.")
        sys.exit(0)

    report_content = report_path.read_text(encoding="utf-8")
    
    # Determine verdict and state
    is_approved = ("APPROVED" in report_content.upper() or "APROVADO" in report_content.upper()) and not ("REJECTED" in report_content.upper() or "REPROVADO" in report_content.upper())
    state = "success" if is_approved else "failed"
    verdict_text = "APPROVED" if is_approved else "REJECTED"

    print(f"[INFO] Gatekeeper Verdict: {verdict_text} (GitLab State: {state})")

    if args.dry_run:
        print("[INFO] DRY RUN enabled. Payload would be:")
        print(f"Target Project: {args.project_id}")
        print(f"Branch: {args.branch} | Commit: {args.sha}")
        print(f"Report length: {len(report_content)} characters")
        return

    if not args.token or not args.project_id:
        print("[WARN] GITLAB_TOKEN or GITLAB_PROJECT_ID not set. Skipping GitLab API reporting.")
        print("[INFO] Report was generated locally for CI logs.")
        return

    # 1. Update Commit Status
    if args.sha:
        desc = f"AI Gatekeeper verdict: {verdict_text}"
        print(f"[INFO] Updating GitLab commit status for {args.sha[:8]} -> {state}...")
        status_ok = update_commit_status(args.gitlab_url, args.project_id, args.sha, state, desc, args.target_url, args.token)
        if status_ok:
            print("[SUCCESS] GitLab commit status updated successfully.")
        else:
            print("[WARN] Could not update GitLab commit status.")

    # 2. Find and Comment on MR
    if args.branch:
        print(f"[INFO] Searching active Merge Request for branch '{args.branch}'...")
        mr = find_active_mr(args.gitlab_url, args.project_id, args.branch, args.token)
        if mr and "iid" in mr:
            mr_iid = mr["iid"]
            print(f"[INFO] Found GitLab MR !{mr_iid} ('{mr.get('title')}'). Posting review note...")
            note_ok = post_mr_note(args.gitlab_url, args.project_id, mr_iid, report_content, args.token)
            if note_ok:
                print(f"[SUCCESS] Review report successfully published to GitLab MR !{mr_iid}")
            else:
                print(f"[WARN] Failed to post review report to MR !{mr_iid}")
        else:
            print(f"[INFO] No open MR found for source branch '{args.branch}'. Skipping MR comment.")


if __name__ == "__main__":
    main()
