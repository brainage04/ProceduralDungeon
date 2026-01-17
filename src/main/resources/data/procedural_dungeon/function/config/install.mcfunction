tellraw @a [{"text":"brainage04's Procedural Structures ","color": "green"},{"text": "installed.","color": "green"}]

function procedural_dungeon:sounds/click

# Add Triggers
scoreboard objectives add procedural_dungeon.about trigger {"text": "About","color": "green"}
scoreboard objectives add procedural_dungeon.config trigger {"text": "Config","color": "green"}

# Add New Player Joined Flag
scoreboard objectives add procedural_dungeon.new_player_joined dummy
scoreboard players set @a procedural_dungeon.new_player_joined 0

# Add Install Flag
scoreboard objectives add procedural_dungeon.installed dummy
scoreboard players set #procedural_dungeon procedural_dungeon.installed 1