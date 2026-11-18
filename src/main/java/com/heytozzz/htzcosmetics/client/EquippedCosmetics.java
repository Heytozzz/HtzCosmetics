package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which cosmetic ids each player wears: at most one per category. Read every frame, so lookups are cheap. */
public final class EquippedCosmetics {
    private static final Map<UUID, Map<Category, String>> BY_PLAYER = new ConcurrentHashMap<>();

    private EquippedCosmetics() {
    }

    public static Collection<String> get(UUID player) {
        Map<Category, String> map = BY_PLAYER.get(player);
        return map == null ? List.of() : map.values();
    }

    public static void equip(UUID player, CosmeticDefinition def) {
        BY_PLAYER.compute(player, (k, old) -> {
            Map<Category, String> next = old == null ? new EnumMap<>(Category.class) : new EnumMap<>(old);
            next.put(def.category(), def.id());
            return Map.copyOf(next);
        });
    }

    /** @return true if the cosmetic was being worn */
    public static boolean unequip(UUID player, String id) {
        boolean[] removed = {false};
        BY_PLAYER.computeIfPresent(player, (k, old) -> {
            Map<Category, String> next = new EnumMap<>(Category.class);
            for (Map.Entry<Category, String> e : old.entrySet()) {
                if (e.getValue().equals(id)) removed[0] = true;
                else next.put(e.getKey(), e.getValue());
            }
            return next.isEmpty() ? null : Map.copyOf(next);
        });
        return removed[0];
    }

    public static void clear(UUID player) {
        BY_PLAYER.remove(player);
    }
}
