#!/bin/bash
# Reply Pilot auto-updater. Start it once and leave its window open (minimizing is fine).
# Whenever Claude saves a new update in this folder, this Mac, by itself:
#   1. updates the Railway server (only when the server code changed),
#   2. builds and checks the app (only when the app changed),
#   3. pushes the update to GitHub,
# then shows a notification when the new app is ready to install.
# It never deletes anything. Close the window to stop it.
{
cd "$(dirname "$0")" || exit 1
export REPLY_PILOT_AUTO=1                  # the three scripts skip their "close this window" line
BRANCH="codex/reply-pilot"
REMOTE="https://github.com/DD1000/ReplyPilot.git"
BUNDLE=".local-updates/reply-pilot-updates.bundle"
READY=".local-updates/ready.txt"          # Claude writes this LAST, after every file is saved
DONE=".local-updates/done.txt"            # the last update this Mac finished
SERVER=".local-updates/server-commit.txt" # the last update deployed to Railway
STATUS=".local-updates/status.txt"        # what the auto-updater is doing now (Claude reads it)
BEAT=".local-updates/heartbeat.txt"       # refreshed every few minutes while this window is open
MARK=".local-updates/run-started"
PIDFILE=".local-updates/auto-updater.pid"
LOG="auto-update-log.txt"
mkdir -p .local-updates
stamp(){ date '+%Y-%m-%d %H:%M:%S'; }
say(){ echo "$(stamp)  $*" | tee -a "$LOG"; }
status(){ echo "$(stamp) $*" > "$STATUS"; }
notify(){ osascript -e "display notification \"$1\" with title \"Reply Pilot\"" >/dev/null 2>&1; }
# A step succeeded only if it rewrote its log after it started and the log says so.
ran(){ [ "$1" -nt "$MARK" ] && grep -q "$2" "$1"; }
step(){ touch "$MARK"; caffeinate -i bash "$1" 2>&1 || true; }
command -v caffeinate >/dev/null 2>&1 || caffeinate(){ shift; "$@"; }

# Only one auto-updater at a time.
OLD=$(cat "$PIDFILE" 2>/dev/null)
if [ -n "$OLD" ] && [ "$OLD" != "$$" ] && ps -p "$OLD" -o command= 2>/dev/null | grep -q "Auto Updater"; then
  echo "The Reply Pilot auto-updater is already running in another window. You can close this one."; exit 0
fi
echo "$$" > "$PIDFILE"
command -v git >/dev/null 2>&1 || { echo "git isn't installed on this Mac."; status "FAILED git is missing"; exit 1; }
[ -d .git ] || { git init -q && git remote add origin "$REMOTE"; } || { status "FAILED could not set up git"; exit 1; }
git config core.fileMode false   # saved files can have different permission bits; only content matters

say "Reply Pilot auto-updater started. Watching for updates from Claude. Leave this window open (minimizing is fine)."
status "IDLE watching"; LAST_BEAT=0; SKIP=""
while true; do
  NOW=$(date +%s); if [ $((NOW-LAST_BEAT)) -ge 300 ]; then stamp > "$BEAT"; LAST_BEAT=$NOW; fi
  KEY=$(tr -d '[:space:]' 2>/dev/null < "$READY"); WANT=${KEY:0:40}
  HAVE=$(head -1 "$DONE" 2>/dev/null | tr -d '[:space:]')
  # Nothing new, or an update that already failed: wait until Claude saves a new one (or asks for a retry).
  if ! [[ "$WANT" =~ ^[0-9a-f]{40}$ ]] || [ "$WANT" = "$HAVE" ] || [ "$KEY" = "$SKIP" ]; then sleep 20; continue; fi
  SHORT=${WANT:0:7}

  # The update file and every saved file must match the update Claude announced.
  if ! git fetch -q "$BUNDLE" "$BRANCH:refs/remotes/updates/$BRANCH" 2>/dev/null || [ "$(git rev-parse -q --verify "refs/remotes/updates/$BRANCH")" != "$WANT" ]; then
    status "WAITING update file for $SHORT isn't complete yet"; sleep 20; continue
  fi
  git update-ref "refs/heads/$BRANCH" "$WANT" && git symbolic-ref HEAD "refs/heads/$BRANCH" && git reset -q
  if [ -n "$(git status --porcelain | grep -v '^??')" ]; then
    status "WAITING files for $SHORT are still being saved"; sleep 20; continue
  fi

  say "New update from Claude: $SHORT — $(git log -1 --format=%s "$WANT")"
  FAILED=""; BUILT=""
  # 1. Server, only when relay/ changed since the last deploy.
  DEPLOYED=$(head -1 "$SERVER" 2>/dev/null | tr -d '[:space:]')
  if [[ "$DEPLOYED" =~ ^[0-9a-f]{40}$ ]] && git cat-file -e "$DEPLOYED" 2>/dev/null && git diff --quiet "$DEPLOYED" "$WANT" -- relay; then
    say "Server code unchanged, skipping the server update."
  else
    status "RUNNING server update for $SHORT"; say "Updating the server on Railway..."
    step "Update Server.command"
    if ran server-update-log.txt "SERVER UPDATE: SUCCESS"; then echo "$WANT" > "$SERVER"; say "Server updated."; else FAILED="server update (see server-update-log.txt)"; fi
  fi
  # 2. App, only when something the app is built from changed since the last finished update.
  if [ -z "$FAILED" ]; then
    if [[ "$HAVE" =~ ^[0-9a-f]{40}$ ]] && git cat-file -e "$HAVE" 2>/dev/null && git diff --quiet "$HAVE" "$WANT" -- app build.gradle settings.gradle gradle.properties gradle gradlew; then
      say "App unchanged, skipping the build."
    else
      status "RUNNING app build for $SHORT"; say "Building the app (a few minutes)..."
      step "Build Reply Pilot.command"
      if ran build-log.txt "BUILD RESULT: SUCCESS"; then BUILT=$(grep -o 'dist/Reply-Pilot-[^ ]*\.apk' build-log.txt | tail -1); say "Built $BUILT (signed with your phone's key)."; else FAILED="app build (see build-log.txt)"; fi
    fi
  fi
  # 3. GitHub.
  if [ -z "$FAILED" ]; then
    status "RUNNING GitHub push for $SHORT"; say "Pushing to GitHub..."
    step "Push to GitHub.command"
    if ran push-log.txt "PUSH RESULT: SUCCESS"; then say "Pushed to GitHub."; else FAILED="GitHub push (see push-log.txt)"; fi
  fi

  if [ -n "$FAILED" ]; then
    SKIP="$KEY"; say "PROBLEM: stopped at the $FAILED. Claude will check it; nothing else runs until the next update."
    status "FAILED $WANT at $FAILED"; notify "Update $SHORT stopped at the $FAILED."; continue
  fi
  echo "$WANT" > "$DONE"
  if [ -n "$BUILT" ]; then
    say "Done. Install $BUILT on your phone."; status "SUCCESS $WANT $BUILT"
    notify "$(basename "$BUILT") is ready to install on your phone."; open -R "$BUILT" 2>/dev/null
  else
    say "Done."; status "SUCCESS $WANT"
  fi
  say "Watching for the next update..."
done
exit
}
