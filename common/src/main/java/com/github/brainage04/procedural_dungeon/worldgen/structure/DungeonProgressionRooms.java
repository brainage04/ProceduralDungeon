package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Templates and pools of the dungeon progression: the guaranteed boss room and boss key vault that the layout compiler
 * attaches to every dungeon, and the optional miniboss rooms that appear through the ordinary room pool.
 */
public final class DungeonProgressionRooms {
    public static final Identifier BOSS_ROOM = ProceduralDungeon.of("dungeon/critical/boss_room");
    public static final Identifier BOSS_KEY_VAULT = ProceduralDungeon.of("dungeon/critical/boss_key_vault");
    public static final Identifier MINIBOSS_ROOM = ProceduralDungeon.of("dungeon/hallway/room/miniboss");

    public static final String BOSS_ROOM_POOL = "critical/boss_room";
    public static final String BOSS_KEY_VAULT_POOL = "critical/boss_key_vault";

    private DungeonProgressionRooms() {}

    /**
     * The key that opens the door of a guarded room template, or empty for every other template.
     */
    public static Optional<DungeonKeyType> guardedRoomKey(Identifier template) {
        if (template.equals(BOSS_ROOM)) {
            return Optional.of(DungeonKeyType.BOSS);
        }
        if (template.equals(MINIBOSS_ROOM)) {
            return Optional.of(DungeonKeyType.MINIBOSS);
        }
        return Optional.empty();
    }

    /**
     * The pool of a dungeon variant, e.g. {@code procedural_dungeon:dungeon/overworld/cobblestone/tier_1/critical/boss_room}.
     */
    public static ResourceKey<StructureTemplatePool> pool(Identifier variant, String pool) {
        return ResourceKey.create(
                Registries.TEMPLATE_POOL,
                Identifier.fromNamespaceAndPath(variant.getNamespace(), "%s/%s".formatted(variant.getPath(), pool))
        );
    }
}
