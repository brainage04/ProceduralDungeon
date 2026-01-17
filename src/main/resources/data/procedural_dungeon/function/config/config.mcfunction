tellraw @s {"text": "                                ","color": "gray","strikethrough": true}
tellraw @s {"text": "brainage04's Procedural Structures | Config","color": "gray"}
tellraw @s {"text": "                                ","color": "gray","strikethrough": true}

tellraw @s {"text": "No config section yet.","color": "gray"}
tellraw @s ""

# Install/Uninstall Functions
tellraw @s {"text": "INSTALL DATAPACK","color": "green","hover_event": {"action": "show_text","value": [{"text": "Click to INSTALL this datapack.","color": "green"}]},"click_event": {"action": "suggest_command","command": "/function procedural_dungeon:config/install"}}
tellraw @s {"text": "UNINSTALL DATAPACK","color": "red","hover_event": {"action": "show_text","value": [{"text": "Click to UNINSTALL this datapack.","color": "red"}]},"click_event": {"action": "suggest_command","command": "/function procedural_dungeon:config/uninstall"}}

tellraw @s {"text": "                                ","color": "gray","strikethrough": true}

function procedural_dungeon:sounds/click

# Reset Trigger
scoreboard players set @s procedural_dungeon.config 0
scoreboard players enable @s procedural_dungeon.config