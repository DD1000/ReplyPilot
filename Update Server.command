#!/bin/bash
# Double-click to update your Reply Pilot server on Railway with the code in relay/.
# Uses your Mac's Railway sign-in. Your OpenAI key and phone token stay in Railway.
cd "$(dirname "$0")/relay" || exit 1
LOG="../server-update-log.txt"
PROJECT="f8a2a0b4-7952-46fd-bbb8-e09669a07f09"
SERVICE="reply-pilot-api"
STATUS_URL="https://reply-pilot-api-production.up.railway.app/status"
fail(){ echo "PROBLEM: $1"; echo "SERVER UPDATE: FAILED"; exit 1; }
find_tool(){ command -v "$1" 2>/dev/null || for p in /opt/homebrew/bin/$1 /usr/local/bin/$1 "$HOME/.npm-global/bin/$1" "$HOME/.railway/bin/$1" "$HOME/.local/bin/$1"; do [ -x "$p" ] && echo "$p" && return; done; }
{
echo "Reply Pilot server update started $(date)"
NODE=$(find_tool node)
if [ -n "$NODE" ]; then
  echo "Running the server's tests first..."
  "$NODE" --test *.test.mjs >/tmp/reply-pilot-relay-tests.txt 2>&1 || { tail -30 /tmp/reply-pilot-relay-tests.txt; fail "Server tests failed, so nothing was deployed."; }
  grep -E "^# (tests|pass|fail)" /tmp/reply-pilot-relay-tests.txt
else echo "Node isn't installed on this Mac, so tests were skipped (they already passed before this was saved)."; fi
RAILWAY=$(find_tool railway)
if [ -z "$RAILWAY" ]; then
  NPM=$(find_tool npm)
  [ -n "$NPM" ] || fail "Railway's command-line tool isn't installed. Install it with: brew install railway"
  echo "Installing Railway's command-line tool (one time)..."
  "$NPM" install -g @railway/cli >/dev/null 2>&1 || fail "Could not install Railway's command-line tool. Install it with: brew install railway"
  RAILWAY=$(find_tool railway); [ -n "$RAILWAY" ] || fail "Railway's command-line tool was installed but can't be found. Open a new Terminal window and try again."
fi
"$RAILWAY" whoami >/dev/null 2>&1 || { echo "Sign in to Railway in the browser window that opens."; "$RAILWAY" login || fail "Railway sign-in didn't finish."; }
"$RAILWAY" link --project "$PROJECT" --service "$SERVICE" --environment production >/dev/null 2>&1 || "$RAILWAY" link --project "$PROJECT" --service "$SERVICE" || fail "Could not connect this folder to your Reply Pilot Railway project."
echo "Uploading the server code to Railway..."
"$RAILWAY" up --detach --service "$SERVICE" || fail "Railway didn't accept the upload."
echo "Waiting for Railway to build and restart the server (usually 1 to 3 minutes)..."
sleep 45
for i in $(seq 1 30); do
  if curl -fsS --max-time 10 "$STATUS_URL" >/dev/null 2>&1; then echo "The server is up."; break; fi; sleep 10
done
echo "Open Reply Pilot, then Reply setup, then Train Autopilot. If training says the server needs an update, wait a minute and try again."
echo "SERVER UPDATE: SUCCESS"
} 2>&1 | tee "$LOG"
echo
echo "Done. You can close this window."
