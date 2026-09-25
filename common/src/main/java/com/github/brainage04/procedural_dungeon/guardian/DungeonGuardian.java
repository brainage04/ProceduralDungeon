package com.github.brainage04.procedural_dungeon.guardian;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;

/**
 * Placeholder guardians of the boss and miniboss rooms: a vanilla mob with tier-scaled health and gear. Room templates
 * mark the spawn point with a data structure block whose metadata is {@link #marker()}.
 */
public enum DungeonGuardian {
    BOSS("boss", EntityTypes.WITHER_SKELETON, "Dungeon Warden", 60.0, 40.0, true),
    MINIBOSS("miniboss", EntityTypes.VINDICATOR, "Dungeon Sentinel", 30.0, 15.0, false);

    public static final String TAG = ProceduralDungeon.MOD_ID + ".guardian";

    private final String name;
    private final EntityType<? extends Mob> type;
    private final String displayName;
    private final double baseHealth;
    private final double healthPerTier;
    private final boolean armored;

    DungeonGuardian(
            String name,
            EntityType<? extends Mob> type,
            String displayName,
            double baseHealth,
            double healthPerTier,
            boolean armored
    ) {
        this.name = name;
        this.type = type;
        this.displayName = displayName;
        this.baseHealth = baseHealth;
        this.healthPerTier = healthPerTier;
        this.armored = armored;
    }

    public String marker() {
        return ProceduralDungeon.of(name).toString();
    }

    public static Optional<DungeonGuardian> fromMarker(String metadata) {
        for (DungeonGuardian guardian : values()) {
            if (guardian.marker().equals(metadata)) {
                return Optional.of(guardian);
            }
        }
        return Optional.empty();
    }

    public double maxHealth(int tier) {
        return baseHealth + healthPerTier * tier;
    }

    /**
     * Spawns the guardian standing on {@code pos}, facing the room entrance (template north).
     */
    public void spawn(ServerLevelAccessor level, BlockPos pos, Rotation rotation, int tierNumber) {
        Mob mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null) {
            return;
        }

        DungeonTier tier = DungeonTier.values()[Math.clamp(tierNumber, 1, DungeonTier.values().length) - 1];
        float yaw = rotation.rotate(Direction.NORTH).toYRot();
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);

        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth(tier.tier));
        }
        mob.setHealth(mob.getMaxHealth());
        equip(mob, EquipmentSlot.MAINHAND, tier.sword);
        if (armored) {
            equip(mob, EquipmentSlot.HEAD, tier.helmet);
            equip(mob, EquipmentSlot.CHEST, tier.chestplate);
            equip(mob, EquipmentSlot.LEGS, tier.leggings);
            equip(mob, EquipmentSlot.FEET, tier.boots);
        }
        mob.setCustomName(Component.literal(displayName));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        level.addFreshEntityWithPassengers(mob);
    }

    private static void equip(Mob mob, EquipmentSlot slot, Item item) {
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, 0.0F);
    }
}
