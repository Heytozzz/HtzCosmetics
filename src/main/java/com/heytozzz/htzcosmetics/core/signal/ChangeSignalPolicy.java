package com.heytozzz.htzcosmetics.core.signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "My cosmetics changed" signal between modded clients, carried by the Minecraft server itself.
 *
 * <p>Every player's "displayed skin parts" byte is sent by the client and relayed by the server to everyone who can
 * see that player. Vanilla only uses the low 7 bits; we flip the unused top bit ({@link #SIGNAL_BIT}) after saving
 * new cosmetics. Nearby modded clients notice the flip and re-ask the backend about that player right away instead of
 * waiting for the normal 60-second refresh. The signal carries no data at all: receivers still get the cosmetics
 * from the backend, so a fake signal can only cause a (rate-limited) extra lookup.
 *
 * <p>Abuse limits: a sender flips at most once per {@link #SEND_COOLDOWN_MS}; a receiver honors at most one signal
 * per player per {@link #PER_PLAYER_COOLDOWN_MS} and at most {@link #MAX_FRESH_PER_MINUTE} in total; signals over
 * the limit are not lost, they are coalesced and handled once the limit allows.
 */
public final class ChangeSignalPolicy {
    public static final int SIGNAL_BIT = 0x80;
    public static final long SEND_COOLDOWN_MS = 5_000;
    public static final long PER_PLAYER_COOLDOWN_MS = 10_000;
    public static final int MAX_FRESH_PER_MINUTE = 12;

    private ChangeSignalPolicy() {
    }

    /** Sender side: decides when to flip the bit. */
    public static final class Sender {
        private long lastSent = Long.MIN_VALUE / 2;
        private boolean pending;

        /** @return true if the bit should be flipped now; otherwise it is flipped later by {@link #poll} */
        public boolean request(long nowMs) {
            if (nowMs - lastSent >= SEND_COOLDOWN_MS) {
                lastSent = nowMs;
                pending = false;
                return true;
            }
            pending = true;
            return false;
        }

        /** Call regularly: true when a postponed signal may now be sent. */
        public boolean poll(long nowMs) {
            if (pending && nowMs - lastSent >= SEND_COOLDOWN_MS) {
                pending = false;
                lastSent = nowMs;
                return true;
            }
            return false;
        }
    }

    /** Receiver side: turns observed bit flips into rate-limited "refresh this player now" decisions. */
    public static final class Receiver {
        private final Map<UUID, Boolean> lastBit = new HashMap<>();
        private final Map<UUID, Long> lastFresh = new HashMap<>();
        private final Set<UUID> deferred = new LinkedHashSet<>();
        private final Deque<Long> window = new ArrayDeque<>();

        /** @return true if this player should be re-fetched right now */
        public boolean observe(UUID player, boolean bit, long nowMs) {
            Boolean previous = lastBit.put(player, bit);
            if (previous == null || previous == bit) return false; // first sight is not a signal
            return tryConsume(player, nowMs);
        }

        /** Players whose earlier signals were postponed and may be re-fetched now. */
        public List<UUID> pollDeferred(long nowMs) {
            List<UUID> ready = new ArrayList<>();
            for (UUID player : new ArrayList<>(deferred)) {
                deferred.remove(player);
                if (tryConsume(player, nowMs)) ready.add(player);
            }
            return ready;
        }

        public void forget(UUID player) {
            lastBit.remove(player);
            lastFresh.remove(player);
            deferred.remove(player);
        }

        private boolean tryConsume(UUID player, long nowMs) {
            Long last = lastFresh.get(player);
            while (!window.isEmpty() && nowMs - window.peekFirst() >= 60_000) window.pollFirst();
            if ((last != null && nowMs - last < PER_PLAYER_COOLDOWN_MS) || window.size() >= MAX_FRESH_PER_MINUTE) {
                deferred.add(player);
                return false;
            }
            window.addLast(nowMs);
            lastFresh.put(player, nowMs);
            return true;
        }
    }
}
