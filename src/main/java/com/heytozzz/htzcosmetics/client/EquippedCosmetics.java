package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.net.BackendClient;
import com.heytozzz.htzcosmetics.core.net.EquippedEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which cosmetics each player wears, and in which colour. Read every frame, so lookups are cheap and all lists are
 * immutable. The renderer draws at most one cosmetic per category (first one wins), whatever the list says.
 *
 * <p>Entries travel as {@code "id"} or {@code "id:rrggbb"} ({@link EquippedEntry}); a colour is only a request:
 * the renderer shows it only if the cosmetic allows it.
 */
public final class EquippedCosmetics {
    /** One player's selection: ids in order, chosen colours by id, and the entries as saved/sent. */
    private record Worn(List<String> ids, Map<String, Integer> colors, List<String> entries) {
        static Worn of(List<EquippedEntry> list) {
            List<String> ids = new ArrayList<>();
            Map<String, Integer> colors = new HashMap<>();
            List<String> entries = new ArrayList<>();
            for (EquippedEntry e : list) {
                if (ids.contains(e.id()) || ids.size() >= BackendClient.MAX_EQUIPPED) continue;
                ids.add(e.id());
                if (e.color() != null) colors.put(e.id(), e.color());
                entries.add(e.format());
            }
            return new Worn(List.copyOf(ids), Map.copyOf(colors), List.copyOf(entries));
        }

        List<EquippedEntry> list() {
            List<EquippedEntry> out = new ArrayList<>();
            for (String id : ids) out.add(new EquippedEntry(id, colors.get(id)));
            return out;
        }
    }

    private static final Map<UUID, Worn> BY_PLAYER = new ConcurrentHashMap<>();

    private EquippedCosmetics() {
    }

    /** Cosmetic ids the player wears (without colours). */
    public static List<String> get(UUID player) {
        Worn w = BY_PLAYER.get(player);
        return w == null ? List.of() : w.ids();
    }

    /** The colour the player chose for a cosmetic (0xRRGGBB), or null for its default colour. */
    public static Integer color(UUID player, String id) {
        Worn w = BY_PLAYER.get(player);
        return w == null ? null : w.colors().get(id);
    }

    /** The selection as saved and sent: "id" or "id:rrggbb". */
    public static List<String> entries(UUID player) {
        Worn w = BY_PLAYER.get(player);
        return w == null ? List.of() : w.entries();
    }

    /** Replaces a player's whole list (from the backend or the saved config); invalid entries are dropped. */
    public static void set(UUID player, List<String> entries) {
        List<EquippedEntry> parsed = new ArrayList<>();
        for (String text : entries) {
            EquippedEntry e = EquippedEntry.parse(text);
            if (e != null) parsed.add(e);
        }
        if (parsed.isEmpty()) BY_PLAYER.remove(player);
        else BY_PLAYER.put(player, Worn.of(parsed));
    }

    /** Equips a cosmetic in {@code color} (null = default), replacing whatever the player wore in its category. */
    public static void equip(UUID player, CosmeticDefinition def, Integer color) {
        BY_PLAYER.compute(player, (k, old) -> {
            List<EquippedEntry> next = new ArrayList<>();
            if (old != null) {
                for (EquippedEntry e : old.list()) {
                    CosmeticDefinition other = CosmeticLibrary.get().definition(e.id());
                    if (!e.id().equals(def.id()) && (other == null || other.category() != def.category())) next.add(e);
                }
            }
            next.add(new EquippedEntry(def.id(), color));
            return Worn.of(next);
        });
    }

    /** Changes the colour of a worn cosmetic (null = back to its default). @return false if it is not worn */
    public static boolean setColor(UUID player, String id, Integer color) {
        boolean[] changed = {false};
        BY_PLAYER.computeIfPresent(player, (k, old) -> {
            if (!old.ids().contains(id)) return old;
            List<EquippedEntry> next = new ArrayList<>();
            for (EquippedEntry e : old.list()) next.add(e.id().equals(id) ? new EquippedEntry(id, color) : e);
            changed[0] = true;
            return Worn.of(next);
        });
        return changed[0];
    }

    /** @return true if the cosmetic was being worn */
    public static boolean unequip(UUID player, String id) {
        boolean[] removed = {false};
        BY_PLAYER.computeIfPresent(player, (k, old) -> {
            List<EquippedEntry> next = old.list();
            removed[0] = next.removeIf(e -> e.id().equals(id));
            return next.isEmpty() ? null : Worn.of(next);
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
