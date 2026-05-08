#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
run_dir="$repo_root/run"
server_properties="$run_dir/server.properties"
backup_properties="$repo_root/build/profiling/server.properties.pre-void-flat-jfr"
world_name="${DUNGEON_BENCHMARK_WORLD:-world-benchmark-void-flat}"
samples="${DUNGEON_BENCHMARK_SAMPLES:-12}"
spacing="${DUNGEON_BENCHMARK_SPACING:-384}"
chunk_radius="${DUNGEON_BENCHMARK_CHUNK_RADIUS:-8}"
seed="${DUNGEON_BENCHMARK_SEED:-48879}"
origin_y="${DUNGEON_BENCHMARK_ORIGIN_Y:-64}"
java_exe="${JAVA_EXE:-java}"
profile_dir="$repo_root/build/profiling"
log_file="$profile_dir/tier5-all-themes-void-flat.log"
jfr_file="$profile_dir/tier5-all-themes-void-flat.jfr"
summary_file="$profile_dir/tier5-all-themes-void-flat-summary.txt"
jfr_name="VoidFlatDungeonProfile"
server_pid=""

mkdir -p "$profile_dir"
cp "$server_properties" "$backup_properties"

restore_properties() {
    if [[ -f "$backup_properties" ]]; then
        cp "$backup_properties" "$server_properties"
        rm -f "$backup_properties"
    fi
}

stop_server() {
    if [[ -n "${SERVER[1]:-}" ]]; then
        printf 'stop\n' >&"${SERVER[1]}" 2>/dev/null || true
    fi
    if [[ -n "$server_pid" ]]; then
        wait "$server_pid" 2>/dev/null || true
    fi
}

cleanup() {
    set +e
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        stop_server
    fi
    restore_properties
}
trap cleanup EXIT

write_void_flat_properties() {
    local generator_settings='{"layers":[],"biome":"minecraft:the_void","structure_overrides":[]}'
    awk -v world="$world_name" -v generator_settings="$generator_settings" '
        BEGIN {
            seen_generate = 0
            seen_generator = 0
            seen_level_name = 0
            seen_level_type = 0
            seen_pause = 0
        }
        /^generate-structures=/ {
            print "generate-structures=false"
            seen_generate = 1
            next
        }
        /^generator-settings=/ {
            print "generator-settings=" generator_settings
            seen_generator = 1
            next
        }
        /^level-name=/ {
            print "level-name=" world
            seen_level_name = 1
            next
        }
        /^level-type=/ {
            print "level-type=minecraft\\:flat"
            seen_level_type = 1
            next
        }
        /^pause-when-empty-seconds=/ {
            print "pause-when-empty-seconds=0"
            seen_pause = 1
            next
        }
        { print }
        END {
            if (!seen_generate) print "generate-structures=false"
            if (!seen_generator) print "generator-settings=" generator_settings
            if (!seen_level_name) print "level-name=" world
            if (!seen_level_type) print "level-type=minecraft\\:flat"
            if (!seen_pause) print "pause-when-empty-seconds=0"
        }
    ' "$backup_properties" > "$server_properties"
}

wait_for_log() {
    local pattern="$1"
    local description="$2"
    local timeout_seconds="${3:-180}"
    local start
    start="$(date +%s)"

    while true; do
        if rg -q "$pattern" "$log_file" 2>/dev/null; then
            return 0
        fi
        if (( "$(date +%s)" - start > timeout_seconds )); then
            echo "Timed out waiting for $description. Last log lines:" >&2
            tail -80 "$log_file" >&2 || true
            return 1
        fi
        sleep 1
    done
}

find_server_pid() {
    local timeout_seconds=60
    local start
    start="$(date +%s)"

    while true; do
        local pid
        pid="$(jcmd -l | awk '/net.fabricmc.devlaunchinjector.Main nogui/ { print $1; exit }')"
        if [[ -n "$pid" ]]; then
            echo "$pid"
            return 0
        fi
        if (( "$(date +%s)" - start > timeout_seconds )); then
            echo "Timed out waiting for Minecraft server JVM." >&2
            return 1
        fi
        sleep 1
    done
}

rm -rf "$run_dir/$world_name"
rm -f "$log_file" "$jfr_file" "$summary_file"
write_void_flat_properties

echo "Starting void-flat benchmark server..."
coproc SERVER {
    cd "$run_dir"
    "$java_exe" \
        -Dfabric.dli.config="$repo_root/.gradle/loom-cache/launch.cfg" \
        -Dfabric.dli.env=server \
        -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer \
        @"$repo_root/build/loom-cache/argFiles/runServer" \
        --sun-misc-unsafe-memory-access=allow \
        --enable-native-access=ALL-UNNAMED \
        -Dfile.encoding=UTF-8 \
        -Duser.country \
        -Duser.language=en \
        -Duser.variant \
        net.fabricmc.devlaunchinjector.Main nogui
} > >(tee "$log_file") 2>&1
server_pid="$SERVER_PID"

wait_for_log 'Done \([0-9.]+s\)! For help, type "help"' "server startup" 180
minecraft_pid="$(find_server_pid)"

echo "Preloading spaced void-flat chunks..."
printf 'execute positioned 0 %s 0 run benchmarkdungeons preload %s %s %s\n' "$origin_y" "$samples" "$spacing" "$chunk_radius" >&"${SERVER[1]}"
wait_for_log 'Preloaded and force-loaded .* chunks' "chunk preload" 180

echo "Starting JFR recording at $jfr_file..."
jcmd "$minecraft_pid" JFR.start name="$jfr_name" settings=profile filename="$jfr_file" >/dev/null

echo "Running tier 5 all-theme dungeon benchmark..."
printf 'execute positioned 0 %s 0 run benchmarkdungeons generation %s %s %s\n' "$origin_y" "$samples" "$spacing" "$seed" >&"${SERVER[1]}"
wait_for_log 'Tier 5 benchmark completed' "dungeon benchmark" 600

echo "Stopping JFR recording..."
jcmd "$minecraft_pid" JFR.dump name="$jfr_name" filename="$jfr_file" >/dev/null || true
jcmd "$minecraft_pid" JFR.stop name="$jfr_name" >/dev/null || true

rg 'Preloaded and force-loaded|Tier 5 .*: total|  detail:|  graph:|Tier 5 benchmark completed|Invalid block entity|DUMMY block entity|POI data mismatch|Failed to create block entity' "$log_file" > "$summary_file" || true

stop_server
restore_properties

echo "Void-flat dungeon benchmark complete."
echo "Log: $log_file"
echo "Summary: $summary_file"
echo "JFR: $jfr_file"
