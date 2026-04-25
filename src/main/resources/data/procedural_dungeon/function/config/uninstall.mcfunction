tellraw @a [{"text":"Procedural Dungeon ","color": "green"},{"text": "uninstalled.","color": "red"}]

function procedural_dungeon:sounds/click

# Remove Triggers
scoreboard objectives remove procedural_dungeon.about
scoreboard objectives remove procedural_dungeon.config

# Remove New Player Joined Flag
scoreboard objectives remove procedural_dungeon.new_player_joined

# Remove Install Flag
scoreboard objectives remove procedural_dungeon.installed