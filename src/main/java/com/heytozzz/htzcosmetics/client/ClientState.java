package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.config.ClientConfig;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared client state: player options and the background thread used for network and file work. */
public final class ClientState {
    /** All blocking work (HTTP, files) runs here, never on the render thread. */
    public static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HtzCosmetics-network");
        t.setDaemon(true);
        return t;
    });

    private static volatile ClientConfig config = new ClientConfig();
    private static Path configFile;

    private ClientState() {
    }

    public static void load() {
        configFile = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(HtzCosmetics.MOD_ID + ".json");
        config = ClientConfig.load(configFile);
    }

    public static ClientConfig config() {
        return config;
    }

    /** Writes the options file in the background. */
    public static void saveConfig() {
        Path file = configFile;
        if (file == null) return;
        ClientConfig snapshot = copy(config);
        NETWORK.execute(() -> {
            try {
                snapshot.save(file);
            } catch (Exception e) {
                HtzCosmetics.LOGGER.warn("Could not save HtzCosmetics options: {}", e.toString());
            }
        });
    }

    /** The local player's account UUID (also valid on the title screen). */
    public static UUID self() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : mc.getUser().getProfileId();
    }

    private static ClientConfig copy(ClientConfig c) {
        ClientConfig n = new ClientConfig();
        n.showOthers = c.showOthers;
        n.showOwn = c.showOwn;
        n.showOwnFirstPerson = c.showOwnFirstPerson;
        n.sendChangeSignal = c.sendChangeSignal;
        n.receiveChangeSignal = c.receiveChangeSignal;
        n.equipped = new ArrayList<>(List.copyOf(c.equipped));
        return n;
    }
}
