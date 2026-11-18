package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.net.BackendClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which cosmetic ids each player wears. Read every frame, so lookups are cheap and lists are immutable.
 * The renderer draws at most one cosmetic per category (first one wins), whatever the list says.
 */
public final class EquippedCosmetics {
    private static final Map<UUID, List<String>> BY_PLAYER = new ConcurrentHashMap<>();

    private EquippedCosmetics() {
    }

    public static List<String> get(UUID player) {
        List<String> ids = BY_PLAYER.get(player);
        return ids == null ? List.of() : ids;
    }

    /** Replaces a player's whole list (from the backend). */
    public static void set(UUID player, List<String> ids) {
        if (ids.isEmpty()) BY_PLAYER.remove(player);
        else BY_PLAYER.put(player, List.copyOf(ids.subList(0, Math.min(ids.size(), BackendClient.MAX_EQUIPPED))));
    }

    /** Equips a cosmetic, replacing whatever the player wore in the same category. */
    public static void equip(UUID player, CosmeticDefinition def) {
        BY_PLAYER.compute(player, (k, old) -> {
            List<String> next = new ArrayList<>();
            if (old != null) {
                for (String id : old) {
                    CosmeticDefinition other = CosmeticLibrary.get().definition(id);
                    if (!id.equals(def.id()) && (other == null || other.category() != def.category())) next.add(id);
                }
            }
            next.add(def.id());
            return List.copyOf(next);
        });
    }

    /** @return true if the cosmetic was being worn */
    public static boolean unequip(UUID player, String id) {
        boolean[] removed = {false};
        BY_PLAYER.computeIfPresent(player, (k, old) -> {
            List<String> next = new ArrayList<>(old);
            removed[0] = next.remove(id);
            return next.isEmpty() ? null : List.copyOf(next);
        });
        return removed[0];
    }

    public static void clear(UUID player) {
        BY_PLAYER.remove(player);
    }

    /** Forgets everyone except {@code keep} (e.g. when leaving a server). */
    public static void clearAllExcept(UUID keep) {
        BY_PLAYER.keySet().removeIf(u -> !u.equals(keep));
    }
}
