#!/bin/bash
# Double-click to send the latest Reply Pilot changes to GitHub
# (DD1000/ReplyPilot, branch codex/reply-pilot). It only adds new changes on top
# of what GitHub already has; it never overwrites or deletes anything there.
cd "$(dirname "$0")" || exit 1
BRANCH="codex/reply-pilot"
REMOTE="https://github.com/DD1000/ReplyPilot.git"
BUNDLE=".local-updates/reply-pilot-updates.bundle"
LOG="push-log.txt"
fail(){ echo "PROBLEM: $1"; echo "PUSH RESULT: FAILED"; exit 1; }
{
echo "Reply Pilot push started $(date)"
command -v git >/dev/null 2>&1 || fail "git isn't installed on this Mac."
[ -f "$BUNDLE" ] || fail "The update file $BUNDLE is missing. Ask Claude to save it again."
if [ ! -d .git ]; then git init -q && git remote add origin "$REMOTE" || fail "Could not set up git in this folder."; fi
git fetch -q "$BUNDLE" "$BRANCH:refs/remotes/updates/$BRANCH" || fail "Could not read the update file."
GH=$(command -v gh || ls /opt/homebrew/bin/gh /usr/local/bin/gh 2>/dev/null | head -1)
if [ -n "$GH" ]; then
  "$GH" auth status >/dev/null 2>&1 || { echo "Log in to GitHub in the browser window that opens."; "$GH" auth login --hostname github.com --git-protocol https --web || fail "GitHub login didn't finish."; }
  "$GH" auth setup-git >/dev/null 2>&1
fi
git fetch -q origin "$BRANCH" || fail "Could not reach GitHub. Check your internet connection and GitHub login."
git merge-base --is-ancestor "origin/$BRANCH" "updates/$BRANCH" || fail "GitHub has changes this folder doesn't know about. Nothing was pushed."
# Point this folder's branch at the updates without touching any files here.
git update-ref "refs/heads/$BRANCH" "updates/$BRANCH" && git symbolic-ref HEAD "refs/heads/$BRANCH" && git reset -q || fail "Could not prepare the branch."
CHANGED=$(git status --porcelain | grep -v '^??' | wc -l | tr -d ' ')
[ "$CHANGED" = "0" ] || echo "Note: $CHANGED file(s) in this folder differ from the saved updates; only the saved updates are pushed."
echo "New changes going to GitHub:"
git log --oneline "origin/$BRANCH..$BRANCH"
git push origin "$BRANCH" || fail "GitHub didn't accept the push."
git branch -q --set-upstream-to="origin/$BRANCH" "$BRANCH" 2>/dev/null
echo "PUSH RESULT: SUCCESS"
} 2>&1 | tee "$LOG"
[ -n "$REPLY_PILOT_AUTO" ] || { echo; echo "Done. You can close this window."; }
