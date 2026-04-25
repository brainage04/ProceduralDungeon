tellraw @a [{"text":"Procedural Dungeon","color": "green"},{"text": " loaded. | ","color": "yellow"},{"text": "ABOUT","color": "green","hover_event": {"action": "show_text","value": [{"text": "Click to learn about Procedural Dungeon.\n","color": "white"},{"text": "Executes command \"/trigger procedural_dungeon.about\"","color": "gray"}]},"click_event": {"action": "run_command","command": "/trigger procedural_dungeon.about"}},{"text": " | ","color": "yellow"},{"text": "CONFIG","color": "green","hover_event": {"action": "show_text","value": [{"text": "Click to configure Procedural Dungeon.\n","color": "white"},{"text": "Executes command \"/trigger procedural_dungeon.config\"","color": "gray"}]},"click_event": {"action": "run_command","command": "/trigger procedural_dungeon.config"}}]

# Check for Install
scoreboard objectives add procedural_dungeon.installed dummy
execute unless score #procedural_dungeon procedural_dungeon.installed matches 1 run function procedural_dungeon:config/install

# Reload 1 Second Loop
schedule clear procedural_dungeon:loops/1_second
function procedural_dungeon:loops/1_second