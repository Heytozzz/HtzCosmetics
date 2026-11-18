package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.net.BackendClient;
import com.heytozzz.htzcosmetics.core.signal.ChangeSignalPolicy;
import com.heytozzz.htzcosmetics.mixin.SkinPartsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Called once per client tick (cheap): keeps other players' cosmetics up to date.
 * <ul>
 *   <li>Every 2 s: nearby players whose data is unknown or older than 60 s are looked up in ONE batched request.</li>
 *   <li>Every tick: watches the change-signal bit of nearby players; a flip triggers a rate-limited "fresh" lookup
 *       of that one player (see {@link ChangeSignalPolicy}).</li>
 * </ul>
 * All requests run on the background thread; never per frame, never on the render thread.
 */
public final class RemoteSync {
    private static final long CHECK_EVERY_MS = 2_000;
    private static final long PLAYER_TTL_MS = 60_000;          // matches the backend's 60 s edge cache
    private static final long FORGET_AFTER_MS = 5 * 60_000;    // players out of sight are forgotten
    private static final long ERROR_BACKOFF_MS = 60_000;
    private static final long MANIFEST_REFRESH_MS = 30 * 60_000;

    private static final AtomicBoolean BATCH_IN_FLIGHT = new AtomicBoolean();
    private static final ChangeSignalPolicy.Receiver SIGNALS = new ChangeSignalPolicy.Receiver();

    // Client-thread only.
    private static final Map<UUID, Long> lastFetched = new HashMap<>();
    private static final Map<UUID, Long> lastSeen = new HashMap<>();
    private static long nextCheck;
    private static long nextManifestRefresh = System.currentTimeMillis() + MANIFEST_REFRESH_MS;
    private static volatile long backoffUntil;
    private static boolean wasInWorld;

    private RemoteSync() {
    }

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        OwnCosmetics.tick();
        if (now >= nextManifestRefresh) {
            nextManifestRefresh = now + MANIFEST_REFRESH_MS;
            ClientBootstrap.loadCosmetics(false);
        }
        if (mc.level == null || mc.player == null) {
            if (wasInWorld) { // left the world/server: forget everyone else
                EquippedCosmetics.clearAllExcept(ClientState.self());
                for (UUID id : lastSeen.keySet()) SIGNALS.forget(id);
                lastFetched.clear();
                lastSeen.clear();
                wasInWorld = false;
            }
            return;
        }
        wasInWorld = true;
        BackendClient backend = ClientBootstrap.backend();
        if (backend == null) return;

        UUID self = mc.player.getUUID();
        boolean listenToSignals = ClientState.config().receiveChangeSignal;
        boolean periodic = now >= nextCheck;
        if (periodic) nextCheck = now + CHECK_EVERY_MS;

        List<UUID> due = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            UUID id = p.getUUID();
            // Only real online accounts (version 4 UUIDs); offline-mode players and NPCs cannot have cosmetics.
            if (id.equals(self) || id.version() != 4) continue;
            if (listenToSignals) {
                byte parts = p.getEntityData().get(SkinPartsAccessor.htzcosmetics$skinPartsData());
                boolean bit = (parts & ChangeSignalPolicy.SIGNAL_BIT) != 0;
                if (SIGNALS.observe(id, bit, now)) fetchFresh(backend, id);
            }
            if (!periodic) continue;
            lastSeen.put(id, now);
            Long fetched = lastFetched.get(id);
            if ((fetched == null || now - fetched > PLAYER_TTL_MS) && due.size() < BackendClient.MAX_BATCH) due.add(id);
        }
        if (listenToSignals) {
            for (UUID id : SIGNALS.pollDeferred(now)) fetchFresh(backend, id);
        }
        if (!periodic) return;

        lastSeen.entrySet().removeIf(e -> {
            boolean gone = now - e.getValue() > FORGET_AFTER_MS;
            if (gone) {
                lastFetched.remove(e.getKey());
                SIGNALS.forget(e.getKey());
                EquippedCosmetics.clear(e.getKey());
            }
            return gone;
        });

        if (due.isEmpty() || now < backoffUntil || !BATCH_IN_FLIGHT.compareAndSet(false, true)) return;
        for (UUID id : due) lastFetched.put(id, now);
        ClientState.NETWORK.execute(() -> {
            try {
                Map<UUID, List<String>> result = backend.fetchEquipped(due);
                for (UUID id : due) EquippedCosmetics.set(id, result.getOrDefault(id, List.of()));
            } catch (Exception e) {
                backoffUntil = System.currentTimeMillis() + ERROR_BACKOFF_MS;
                HtzCosmetics.LOGGER.debug("Could not fetch equipped cosmetics: {}", e.toString());
            } finally {
                BATCH_IN_FLIGHT.set(false);
            }
        });
    }

    /** One player signalled a change: ask the backend directly, skipping its cache. */
    private static void fetchFresh(BackendClient backend, UUID player) {
        lastFetched.put(player, System.currentTimeMillis());
        ClientState.NETWORK.execute(() -> {
            try {
                EquippedCosmetics.set(player, backend.fetchEquippedFresh(player));
            } catch (Exception e) {
                HtzCosmetics.LOGGER.debug("Could not refresh a player's cosmetics: {}", e.toString());
            }
        });
    }
}
