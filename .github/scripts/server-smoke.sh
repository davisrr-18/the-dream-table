#!/usr/bin/env bash
# Dedicated-server smoke test for one loader: boots the dev server, waits for "Done (", stops it with the console
# command `stop`, and fails if the server never finishes starting, never saves on shutdown, or the log holds an
# exception from The Dream Table.
#
#   server-smoke.sh <fabric|neoforge>
#
# Environment (all optional):
#   SMOKE_COMMAND   command that starts the server (default: ./gradlew :<loader>:runServer --no-daemon --console=plain)
#   LOG_DIR         where the server log is written (default: $RUNNER_TEMP or /tmp)
#   START_TIMEOUT   seconds to wait for "Done (" (default 1200: includes the Gradle and Loom setup of this runner)
#   STOP_TIMEOUT    seconds to wait for the save line after `stop` (default 180)
#
# The dev JVM can stay alive after a clean stop (the Architectury dev runtime keeps non-daemon pools), so the run ends
# at "All dimensions are saved" and the process is killed afterwards. Vanilla's Yggdrasil key-fetch timeouts are expected
# and are not mod errors.
set -u

loader="${1:?usage: server-smoke.sh <fabric|neoforge>}"
start_timeout="${START_TIMEOUT:-1200}"
stop_timeout="${STOP_TIMEOUT:-180}"
log_dir="${LOG_DIR:-${RUNNER_TEMP:-/tmp}}"
log="$log_dir/server-$loader.log"
command_line="${SMOKE_COMMAND:-./gradlew :$loader:runServer --no-daemon --console=plain}"

mkdir -p "$loader/run" "$log_dir"
echo "eula=true" > "$loader/run/eula.txt"
: > "$log"

# The server reads its console from a FIFO that this script keeps open for writing, so it never sees end of input.
fifo="$(mktemp -u)"
mkfifo "$fifo"
exec 3<>"$fifo"
rm -f "$fifo"

# Job control gives the background server its own process group, so the cleanup can end the whole tree.
set -m
bash -c "$command_line" <&3 >"$log" 2>&1 &
pid=$!

cleanup() {
	kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
	sleep 3
	kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null
	exec 3>&-
}
trap cleanup EXIT

fail() {
	echo "::error::$loader dedicated server smoke failed: $1"
	echo "---- last 80 log lines ----"
	tail -n 80 "$log"
	exit 1
}

waited=0
until grep -Eq 'Done \([0-9.,]+s\)!' "$log"; do
	if ! kill -0 "$pid" 2>/dev/null; then
		fail "the server process exited before \"Done (\" appeared"
	fi
	if [ "$waited" -ge "$start_timeout" ]; then
		fail "\"Done (\" did not appear within ${start_timeout}s"
	fi
	sleep 2
	waited=$((waited + 2))
done
echo "$loader server finished starting after about ${waited}s; sending stop"

echo "stop" >&3

waited=0
until grep -q 'All dimensions are saved' "$log"; do
	if ! kill -0 "$pid" 2>/dev/null; then
		# The process ended without the save line (or after it, in the same poll): decide on the log once more.
		grep -q 'All dimensions are saved' "$log" && break
		fail "the server process exited without saving its dimensions"
	fi
	if [ "$waited" -ge "$stop_timeout" ]; then
		fail "\"All dimensions are saved\" did not appear within ${stop_timeout}s of stop"
	fi
	sleep 2
	waited=$((waited + 2))
done
echo "$loader server stopped cleanly"

# An exception or error from the mod anywhere in the log fails the smoke, even if the server kept running.
problems="$(grep -nE 'at com\.davisr\.thedreamtable\.|/(ERROR|FATAL)\].*(the_dream_table|thedreamtable)|(Exception|Error).*(the_dream_table|thedreamtable)|(the_dream_table|thedreamtable).*(Exception|Error)' "$log" || true)"
if [ -n "$problems" ]; then
	echo "$problems"
	fail "the log has an exception or error from the_dream_table"
fi

echo "$loader dedicated server smoke passed"
