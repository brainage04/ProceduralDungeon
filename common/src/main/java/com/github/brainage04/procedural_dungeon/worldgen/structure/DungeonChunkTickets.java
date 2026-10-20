package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Chunk tickets of staged dungeon jobs. PD owns its ticket type: it only loads chunks (no block or
 * entity ticking) and is never saved, so a restart cannot leak it. Jobs that share a chunk share
 * one ticket through a per-level reference count, so the first job to finish does not unload a
 * chunk another job still needs.
 */
public final class DungeonChunkTickets {
    /** Name the loaders register {@link #TICKET_TYPE} under. */
    public static final String TICKET_NAME = "staged_generation";

    /** Load only, not persistent, no timeout. */
    public static final TicketType TICKET_TYPE =
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    private static final int RADIUS = 0;

    /**
     * Vanilla {@code /forceload} uses level 31; PD before r61 added radius-1 FORCED tickets, which
     * is level 32.
     */
    public static final int LEGACY_FORCED_TICKET_LEVEL = 32;

    private static final Map<ResourceKey<Level>, Long2IntOpenHashMap> REFERENCES =
            new IdentityHashMap<>();

    private DungeonChunkTickets() {}

    static void acquire(ServerLevel level, long chunk) {
        Long2IntOpenHashMap references =
                REFERENCES.computeIfAbsent(level.dimension(), ignored -> new Long2IntOpenHashMap());
        if (references.addTo(chunk, 1) == 0) {
            level.getChunkSource().addTicketWithRadius(TICKET_TYPE, ChunkPos.unpack(chunk), RADIUS);
        }
    }

    static void release(ServerLevel level, long chunk) {
        Long2IntOpenHashMap references = REFERENCES.get(level.dimension());
        if (references == null) {
            return;
        }
        int remaining = references.addTo(chunk, -1) - 1;
        if (remaining <= 0) {
            references.remove(chunk);
            level.getChunkSource()
                    .removeTicketWithRadius(TICKET_TYPE, ChunkPos.unpack(chunk), RADIUS);
        }
    }

    /** Number of jobs holding {@code chunk} in {@code level}. */
    public static int references(ServerLevel level, long chunk) {
        Long2IntOpenHashMap references = REFERENCES.get(level.dimension());
        return references == null ? 0 : references.get(chunk);
    }

    /** Whether the chunk has PD's ticket in the level's ticket storage. */
    public static boolean hasTicket(ServerLevel level, long chunk) {
        for (Ticket ticket : ticketStorage(level).getTickets(chunk)) {
            if (ticket.getType() == TICKET_TYPE) {
                return true;
            }
        }
        return false;
    }

    /** Whether the chunk holds a forced ticket at the level PD used to add before r61. */
    public static boolean hasLegacyForcedTicket(ServerLevel level, long chunk) {
        for (Ticket ticket : ticketStorage(level).getTickets(chunk)) {
            if (ticket.getType() == TicketType.FORCED
                    && ticket.getTicketLevel() == LEGACY_FORCED_TICKET_LEVEL) {
                return true;
            }
        }
        return false;
    }

    /** Removes the radius-1 forced ticket PD added to {@code chunk} before r61. */
    public static boolean releaseLegacyForcedTicket(ServerLevel level, long chunk) {
        if (!hasLegacyForcedTicket(level, chunk)) {
            return false;
        }
        level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, ChunkPos.unpack(chunk), 1);
        return true;
    }

    /** Chunks with any forced ticket; filter with {@link #hasLegacyForcedTicket}. */
    public static LongSet forcedChunks(ServerLevel level) {
        return level.getForceLoadedChunks();
    }

    /**
     * Forgets every reference count. The tickets themselves are not saved, so they end with the
     * server.
     */
    public static void clear() {
        if (!REFERENCES.isEmpty()) {
            ProceduralDungeon.LOGGER.debug(
                    "Clearing staged dungeon ticket references for {} level(s).",
                    REFERENCES.size());
        }
        REFERENCES.clear();
    }

    private static TicketStorage ticketStorage(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
    }
}
