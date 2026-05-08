#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
chunky_launcher="${CHUNKY_LAUNCHER_JAR:-$repo_root/tools/chunky/ChunkyLauncher.jar}"
world_name="${DUNGEON_BENCHMARK_WORLD:-world-benchmark-void-flat}"
samples="${DUNGEON_BENCHMARK_SAMPLES:-12}"
spacing="${DUNGEON_BENCHMARK_SPACING:-384}"
chunk_radius="${DUNGEON_BENCHMARK_CHUNK_RADIUS:-8}"
scene_name="${CHUNKY_SCENE_NAME:-void_flat_tier5_all_themes_grid_overview}"
target="${CHUNKY_RENDER_TARGET:-4}"
threads="${CHUNKY_RENDER_THREADS:-8}"
fov="${CHUNKY_RENDER_FOV:-2200}"
shift_x="${CHUNKY_RENDER_SHIFT_X:-0.35}"
shift_y="${CHUNKY_RENDER_SHIFT_Y:--0.30}"
scene_dir="$repo_root/.chunky/scenes"
snapshot_dir="$scene_dir/snapshots"
template_scene="${CHUNKY_TEMPLATE_SCENE:-$scene_dir/final1080_zoomed_oriented_tier5_d16_sculk_ne.json}"
world_root="$repo_root/run/$world_name"
world_wrapper="$repo_root/scratch/chunky_world_benchmark_void_flat"
render_dir="$repo_root/scratch/renders/benchmark_dungeons"

if [[ ! -f "$chunky_launcher" ]]; then
    echo "Chunky launcher not found: $chunky_launcher" >&2
    exit 1
fi
if [[ ! -f "$template_scene" ]]; then
    echo "Chunky template scene not found: $template_scene" >&2
    echo "Set CHUNKY_TEMPLATE_SCENE to an existing Chunky scene JSON." >&2
    exit 1
fi
if [[ ! -f "$world_root/level.dat" ]]; then
    echo "Benchmark world not found: $world_root" >&2
    exit 1
fi

columns="$(awk -v n="$samples" 'BEGIN { c = int(sqrt(n)); if (c * c < n) c++; print c }')"
rows="$(awk -v n="$samples" -v c="$columns" 'BEGIN { print int((n + c - 1) / c) }')"
center_x="$(awk -v c="$columns" -v s="$spacing" 'BEGIN { printf "%.3f", ((c - 1) * s) / 2 }')"
center_z="$(awk -v r="$rows" -v s="$spacing" 'BEGIN { printf "%.3f", ((r - 1) * s) / 2 }')"
camera_x="$(awk -v x="$center_x" -v s="$spacing" 'BEGIN { printf "%.3f", x + (s * 1.8) }')"
camera_y="$(awk -v s="$spacing" 'BEGIN { printf "%.3f", 750 + (s * 0.35) }')"
camera_z="$(awk -v z="$center_z" -v s="$spacing" 'BEGIN { printf "%.3f", z + (s * 1.8) }')"

min_chunk_x=999999
max_chunk_x=-999999
min_chunk_z=999999
max_chunk_z=-999999
for ((i = 0; i < samples; i++)); do
    column=$((i % columns))
    row=$((i / columns))
    chunk_x=$(((column * spacing) / 16))
    chunk_z=$(((row * spacing) / 16))
    ((chunk_x - chunk_radius < min_chunk_x)) && min_chunk_x=$((chunk_x - chunk_radius))
    ((chunk_x + chunk_radius > max_chunk_x)) && max_chunk_x=$((chunk_x + chunk_radius))
    ((chunk_z - chunk_radius < min_chunk_z)) && min_chunk_z=$((chunk_z - chunk_radius))
    ((chunk_z + chunk_radius > max_chunk_z)) && max_chunk_z=$((chunk_z + chunk_radius))
done

chunk_list="$(mktemp)"
{
    printf '['
    first=1
    for ((x = min_chunk_x; x <= max_chunk_x; x++)); do
        for ((z = min_chunk_z; z <= max_chunk_z; z++)); do
            if [[ "$first" -eq 0 ]]; then
                printf ','
            fi
            first=0
            printf '[%s,%s]' "$x" "$z"
        done
    done
    printf ']\n'
} > "$chunk_list"

rm -rf "$world_wrapper"
mkdir -p "$world_wrapper" "$snapshot_dir" "$render_dir"
ln -s "../../run/$world_name/level.dat" "$world_wrapper/level.dat"
ln -s "../../run/$world_name/dimensions/minecraft/overworld/region" "$world_wrapper/region"
ln -s "../../run/$world_name/dimensions/minecraft/overworld/entities" "$world_wrapper/entities"
ln -s "../../run/$world_name/dimensions/minecraft/overworld/poi" "$world_wrapper/poi"

jq \
    --arg name "$scene_name" \
    --arg world "$world_wrapper" \
    --argjson centerX "$center_x" \
    --argjson centerZ "$center_z" \
    --argjson cameraX "$camera_x" \
    --argjson cameraY "$camera_y" \
    --argjson cameraZ "$camera_z" \
    --argjson target "$target" \
    --argjson fov "$fov" \
    --arg shiftX "$shift_x" \
    --arg shiftY "$shift_y" \
    --slurpfile chunks "$chunk_list" \
    '.name=$name
     | .world.path=$world
     | .world.dimension=0
     | .chunkList=$chunks[0]
     | .width=1920
     | .height=1080
     | .spp=0
     | .sppTarget=$target
     | .renderTime=0
     | .entities=[]
     | .actors=[]
     | .camera.name="camera 1"
     | .camera.position={x:$cameraX,y:$cameraY,z:$cameraZ}
     | .camera.orientation={roll:3.141592653589793,pitch:0.95,yaw:3.9269908169872414}
     | .camera.projectionMode="PARALLEL"
     | .camera.fov=$fov
     | .camera.shift={x:($shiftX | tonumber),y:($shiftY | tonumber)}
     | .yMin=-64
     | .yMax=256
     | .yClipMin=-64
     | .yClipMax=256' \
    "$template_scene" > "$scene_dir/$scene_name.json"
rm "$chunk_list"

rm -f "$scene_dir/$scene_name.dump" "$scene_dir/$scene_name.octree2" "$snapshot_dir/$scene_name"-*.png

echo "Rendering $samples dungeons as a ${columns}x${rows} grid with Chunky..."
echo "Scene: $scene_dir/$scene_name.json"
echo "World wrapper: $world_wrapper"
java -Duser.home="$repo_root" -jar "$chunky_launcher" \
    -render "$scene_name" \
    -scene-dir "$scene_dir" \
    -target "$target" \
    -threads "$threads" \
    -reload-chunks \
    -f

snapshot="$(find "$snapshot_dir" -maxdepth 1 -type f -name "$scene_name-*.png" -printf '%T@ %p\n' | sort -nr | awk 'NR == 1 { print $2 }')"
if [[ -z "$snapshot" ]]; then
    echo "Chunky completed, but no snapshot was found for $scene_name." >&2
    exit 1
fi

render_output="$render_dir/$scene_name.png"
cp "$snapshot" "$render_output"

echo "Render: $render_output"
