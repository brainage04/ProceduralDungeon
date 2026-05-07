package com.github.brainage04.procedural_dungeon.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

public record StagedDungeonPieceSpec(
        StructurePoolElement element,
        BlockPos position,
        Rotation rotation,
        BoundingBox boundingBox,
        int groundLevelDelta
) {
    public CompoundTag save(RegistryOps<Tag> ops) {
        CompoundTag tag = new CompoundTag();
        tag.store("element", StructurePoolElement.CODEC, ops, element);
        tag.putInt("x", position.getX());
        tag.putInt("y", position.getY());
        tag.putInt("z", position.getZ());
        tag.store("rotation", Rotation.LEGACY_CODEC, rotation);
        tag.store("bounding_box", BoundingBox.CODEC, boundingBox);
        tag.putInt("ground_level_delta", groundLevelDelta);
        return tag;
    }

    public static StagedDungeonPieceSpec load(RegistryOps<Tag> ops, CompoundTag tag) {
        StructurePoolElement element = tag.read("element", StructurePoolElement.CODEC, ops).orElseThrow();
        BlockPos position = new BlockPos(
                tag.getIntOr("x", 0),
                tag.getIntOr("y", 0),
                tag.getIntOr("z", 0)
        );
        Rotation rotation = tag.read("rotation", Rotation.LEGACY_CODEC).orElse(Rotation.NONE);
        BoundingBox boundingBox = tag.read("bounding_box", BoundingBox.CODEC).orElseThrow();
        int groundLevelDelta = tag.getIntOr("ground_level_delta", element.getGroundLevelDelta());
        return new StagedDungeonPieceSpec(element, position, rotation, boundingBox, groundLevelDelta);
    }

    public static RegistryOps<Tag> ops(net.minecraft.core.RegistryAccess registryAccess) {
        return registryAccess.createSerializationContext(NbtOps.INSTANCE);
    }
}
