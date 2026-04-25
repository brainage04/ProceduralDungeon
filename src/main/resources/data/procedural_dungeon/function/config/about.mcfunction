tellraw @s {"text": "                                ","color": "gray","strikethrough": true}
tellraw @s {"text": "Procedural Dungeon | About","color": "gray"}
tellraw @s {"text": "                                ","color": "gray","strikethrough": true}

tellraw @s {"text": "No about section yet.","color": "gray"}

tellraw @s {"text": "                                ","color": "gray","strikethrough": true}

function procedural_dungeon:sounds/click

# Reset Trigger
scoreboard players set @s procedural_dungeon.about 0
scoreboard players enable @s procedural_dungeon.about