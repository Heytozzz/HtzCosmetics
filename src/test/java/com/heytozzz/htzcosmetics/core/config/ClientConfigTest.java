package com.heytozzz.htzcosmetics.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    @TempDir
    Path dir;

    @Test
    void roundTrip() throws Exception {
        Path file = dir.resolve("config/htzcosmetics.json");
        ClientConfig c = new ClientConfig();
        c.showOthers = false;
        c.sendChangeSignal = false;
        c.equipped = List.of("top_hat", "wings");
        c.save(file);
        ClientConfig back = ClientConfig.load(file);
        assertFalse(back.showOthers);
        assertFalse(back.sendChangeSignal);
        assertTrue(back.showOwn);
        assertEquals(List.of("top_hat", "wings"), back.equipped);
    }

    @Test
    void brokenOrHostileFilesFallBackToDefaults() throws Exception {
        Path file = dir.resolve("htzcosmetics.json");
        for (String content : new String[]{"not json", "[]", "{\"showOthers\":\"yes\"}", "{".repeat(50_000), ""}) {
            Files.writeString(file, content);
            ClientConfig c = ClientConfig.load(file);
            assertTrue(c.showOthers);
            assertTrue(c.equipped.isEmpty());
        }
        Files.writeString(file, "{\"equipped\":[\"ok\",\"../evil\",\"BAD\",5,\"ok\"],\"unknown\":1}");
        assertEquals(List.of("ok"), ClientConfig.load(file).equipped);
        assertTrue(ClientConfig.load(dir.resolve("missing.json")).showOwn);
    }
}
