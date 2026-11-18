package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.net.AuthClient;
import com.heytozzz.htzcosmetics.core.net.BackendClient;
import com.heytozzz.htzcosmetics.core.net.SecureHttp;
import net.minecraft.client.Minecraft;
import com.heytozzz.htzcosmetics.mixin.UserApiAccessor;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The local player's own cosmetics: equip/unequip, then save to the backend (debounced) and signal nearby players.
 *
 * <p>Saving needs a login, done lazily with the player's Mojang-signed certificate. Accounts without one
 * (offline/dev accounts, some restricted accounts) keep their selection locally only.
 */
public final class OwnCosmetics {
    public enum Status { IDLE, SAVING, SAVED, LOCAL_ONLY, ERROR }

    private static final long SAVE_DELAY_MS = 1_500;
    private static final long RETRY_DELAY_MS = 3_000;
    /** Without a certificate the selection stays local; try again this often (Mojang may have been down). */
    private static final long CERTIFICATE_RETRY_MS = 120_000;

    private static volatile Status status = Status.IDLE;
    // Client thread only.
    private static boolean dirty;
    private static long saveAt;
    private static boolean saving;
    // Network thread only.
    private static AuthClient.Session session;
    private static AuthClient auth;

    private OwnCosmetics() {
    }

    public static Status status() {
        return status;
    }

    /** Client thread, at startup: show the last known selection, then ask the backend for the saved one. */
    public static void init(SecureHttp http, BackendClient backend, AuthClient authClient) {
        auth = authClient;
        UUID self = ClientState.self();
        EquippedCosmetics.set(self, ClientState.config().equipped);
        ClientState.NETWORK.execute(() -> {
            try {
                List<String> saved = backend.fetchEquipped(List.of(self)).getOrDefault(self, List.of());
                Minecraft.getInstance().execute(() -> {
                    if (dirty || saving) return; // the player already changed something: keep that
                    EquippedCosmetics.set(self, saved);
                    ClientState.config().equipped = new ArrayList<>(saved);
                    ClientState.saveConfig();
                });
            } catch (Exception e) {
                HtzCosmetics.LOGGER.debug("Could not load saved cosmetics: {}", e.toString());
            }
        });
    }

    public static void equip(CosmeticDefinition def) {
        EquippedCosmetics.equip(ClientState.self(), def, null);
        markDirty(SAVE_DELAY_MS);
    }

    /** Changes the colour of a worn cosmetic (null = its default colour). */
    public static void setColor(String id, Integer color) {
        if (EquippedCosmetics.setColor(ClientState.self(), id, color)) markDirty(SAVE_DELAY_MS);
    }

    /** The colour chosen for a worn cosmetic, or null for its default. */
    public static Integer color(String id) {
        return EquippedCosmetics.color(ClientState.self(), id);
    }

    public static boolean unequip(String id) {
        boolean removed = EquippedCosmetics.unequip(ClientState.self(), id);
        if (removed) markDirty(SAVE_DELAY_MS);
        return removed;
    }

    public static void unequipAll() {
        EquippedCosmetics.clear(ClientState.self());
        markDirty(SAVE_DELAY_MS);
    }

    public static boolean isEquipped(String id) {
        return EquippedCosmetics.get(ClientState.self()).contains(id);
    }

    private static void markDirty(long delayMs) {
        dirty = true;
        saveAt = System.currentTimeMillis() + delayMs;
        status = Status.SAVING;
    }

    /** Client thread, every tick. */
    public static void tick() {
        ChangeSignal.tick();
        if (!dirty || saving || System.currentTimeMillis() < saveAt) return;
        dirty = false;
        saving = true;
        List<String> ids = List.copyOf(EquippedCosmetics.entries(ClientState.self())); // "id" or "id:rrggbb"
        ClientState.config().equipped = new ArrayList<>(ids);
        ClientState.saveConfig();
        UUID self = ClientState.self();
        BackendClient backend = ClientBootstrap.backend();
        ClientState.NETWORK.execute(() -> {
            Status result = save(backend, self, ids);
            Minecraft.getInstance().execute(() -> {
                saving = false;
                if (dirty) return; // changed again meanwhile: the next save covers it
                status = result;
                if (result == Status.SAVED) ChangeSignal.send();
                if (result == Status.ERROR) markDirty(RETRY_DELAY_MS * 10);
                if (result == Status.LOCAL_ONLY) {
                    // Keep showing "local only", but try again later without bothering the player.
                    dirty = true;
                    saveAt = System.currentTimeMillis() + CERTIFICATE_RETRY_MS;
                }
            });
        });
    }

    /** Network thread. */
    private static Status save(BackendClient backend, UUID self, List<String> ids) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (session == null || !session.validFor(System.currentTimeMillis(), 60_000)) {
                    Optional<AuthClient.Certificate> cert = certificate();
                    if (cert.isEmpty()) return Status.LOCAL_ONLY;
                    session = auth.login(self, cert.get());
                }
                backend.putEquipped(session.token(), ids);
                return Status.SAVED;
            } catch (SecureHttp.HttpStatusException e) {
                if (e.status() == 401) {
                    session = null; // expired or revoked: log in again once
                    continue;
                }
                if (e.status() == 429) {
                    sleepQuietly(RETRY_DELAY_MS);
                    continue;
                }
                HtzCosmetics.LOGGER.warn("Could not save cosmetics: {}", e.getMessage());
                return Status.ERROR;
            } catch (Exception e) {
                HtzCosmetics.LOGGER.warn("Could not save cosmetics: {}", e.toString());
                return Status.ERROR;
            }
        }
        return Status.ERROR;
    }

    /**
     * The Mojang-signed player certificate (the one Minecraft uses for chat), if this account can get one.
     *
     * <p>First from Minecraft's own certificate manager. If that has none (Mojang was briefly unavailable, it did
     * not answer in time, or another mod replaced the manager), ask Mojang again with a fresh manager of the same
     * vanilla class, through Minecraft's own account service: the access token is handled by Minecraft and Mojang's
     * library, never by this mod. The certificate is only used to prove to our backend who the player is; it is not
     * used for chat.
     */
    private static Optional<AuthClient.Certificate> certificate() {
        Minecraft mc = Minecraft.getInstance();
        Optional<ProfileKeyPair> pair = fetch("Minecraft's certificate manager", mc.getProfileKeyPairManager());
        if (pair.isEmpty()) {
            try {
                var service = ((UserApiAccessor) mc).htzcosmetics$userApiService();
                pair = fetch("a fresh certificate request",
                    new AccountProfileKeyPairManager(service, ClientState.self(), mc.gameDirectory.toPath()));
            } catch (Exception e) {
                HtzCosmetics.LOGGER.warn("Could not request a Mojang certificate: {}", e.toString());
            }
        }
        if (pair.isEmpty()) {
            HtzCosmetics.LOGGER.warn("No Mojang player certificate: your cosmetics are saved on this computer only, "
                + "so other players cannot see them yet. Retrying in {} s. (Offline/cracked accounts never get one.)",
                CERTIFICATE_RETRY_MS / 1000);
            return Optional.empty();
        }
        ProfilePublicKey.Data data = pair.get().publicKey().data();
        return Optional.of(new AuthClient.Certificate(data.key(), pair.get().privateKey(),
            data.expiresAt().toEpochMilli(), data.keySignature()));
    }

    private static Optional<ProfileKeyPair> fetch(String from, ProfileKeyPairManager manager) {
        try {
            Optional<ProfileKeyPair> pair = manager.prepareKeyPair().get(30, TimeUnit.SECONDS);
            if (pair.isEmpty()) HtzCosmetics.LOGGER.info("No Mojang certificate from {}", from);
            return pair;
        } catch (Exception e) {
            HtzCosmetics.LOGGER.info("No Mojang certificate from {}: {}", from, e.toString());
            return Optional.empty();
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
