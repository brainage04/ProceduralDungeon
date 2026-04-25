# Check for New Players
execute as @a unless score @s procedural_dungeon.new_player_joined matches 1 run function procedural_dungeon:config/new_player_joined

# Check for Triggers
execute as @a[scores={procedural_dungeon.about=1..}] run function procedural_dungeon:config/about
execute as @a[scores={procedural_dungeon.config=1..}] run function procedural_dungeon:config/config