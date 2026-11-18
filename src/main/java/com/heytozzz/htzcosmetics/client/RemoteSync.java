package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.net.BackendClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Called once per client tick (cheap): every few seconds it collects nearby players whose cosmetics are unknown or
 * stale and asks the backend about them in ONE background request. Never per frame, never on the render thread.
 */
public final class RemoteSync {
    private static final long CHECK_EVERY_MS = 2_000;
    private static final long PLAYER_TTL_MS = 60_000;          // matches the backend's 60 s edge cache
    private static final long FORGET_AFTER_MS = 5 * 60_000;    // players out of sight are forgotten
    private static final long ERROR_BACKOFF_MS = 60_000;
    private static final long MANIFEST_REFRESH_MS = 30 * 60_000;

    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HtzCosmetics-network");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();

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
        if (now >= nextManifestRefresh) {
            nextManifestRefresh = now + MANIFEST_REFRESH_MS;
            ClientBootstrap.loadCosmetics(false);
        }
        if (mc.level == null || mc.player == null) {
            if (wasInWorld) { // left the world/server: forget everyone else
                EquippedCosmetics.clearAllExcept(mc.getUser().getProfileId());
                lastFetched.clear();
                lastSeen.clear();
                wasInWorld = false;
            }
            return;
        }
        wasInWorld = true;
        if (now < nextCheck) return;
        nextCheck = now + CHECK_EVERY_MS;

        UUID self = mc.player.getUUID();
        List<UUID> due = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            UUID id = p.getUUID();
            // Only real online accounts (version 4 UUIDs); offline-mode players and NPCs cannot have cosmetics.
            if (id.equals(self) || id.version() != 4) continue;
            lastSeen.put(id, now);
            Long fetched = lastFetched.get(id);
            if ((fetched == null || now - fetched > PLAYER_TTL_MS) && due.size() < BackendClient.MAX_BATCH) due.add(id);
        }
        lastSeen.entrySet().removeIf(e -> {
            boolean gone = now - e.getValue() > FORGET_AFTER_MS;
            if (gone) {
                lastFetched.remove(e.getKey());
                EquippedCosmetics.clear(e.getKey());
            }
            return gone;
        });

        if (due.isEmpty() || now < backoffUntil || !IN_FLIGHT.compareAndSet(false, true)) return;
        for (UUID id : due) lastFetched.put(id, now);
        BackendClient backend = ClientBootstrap.backend();
        if (backend == null) {
            IN_FLIGHT.set(false);
            return;
        }
        NETWORK.execute(() -> {
            try {
                Map<UUID, List<String>> result = backend.fetchEquipped(due);
                for (UUID id : due) EquippedCosmetics.set(id, result.getOrDefault(id, List.of()));
            } catch (Exception e) {
                backoffUntil = System.currentTimeMillis() + ERROR_BACKOFF_MS;
                HtzCosmetics.LOGGER.debug("Could not fetch equipped cosmetics: {}", e.toString());
            } finally {
                IN_FLIGHT.set(false);
            }
        });
    }
}
