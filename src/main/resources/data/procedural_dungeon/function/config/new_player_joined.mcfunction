tellraw @s [{"text": " | ","color": "yellow"},{"text":"Procedural Dungeon","color": "green"},{"text": " | ","color": "yellow"},{"text": "ABOUT","color": "green","hover_event": {"action": "show_text","value": [{"text": "Click to learn about Procedural Dungeon.\n","color": "white"},{"text": "Executes command \"/trigger procedural_dungeon.about\"","color": "gray"}]},"click_event": {"action": "run_command","command": "/trigger procedural_dungeon.about"}},{"text": " | ","color": "yellow"},{"text": "CONFIG","color": "green","hover_event": {"action": "show_text","value": [{"text": "Click to configure Procedural Dungeon.\n","color": "white"},{"text": "Executes command \"/trigger procedural_dungeon.config\"","color": "gray"}]},"click_event": {"action": "run_command","command": "/trigger procedural_dungeon.config"}},{"text": " | ","color": "yellow"}]

# Flag Player as Joined
scoreboard players set @s procedural_dungeon.new_player_joined 1

# Enable Triggers
scoreboard players enable @s procedural_dungeon.about
scoreboard players enable @s procedural_dungeon.config