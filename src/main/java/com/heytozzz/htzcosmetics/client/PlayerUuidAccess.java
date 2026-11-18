package com.heytozzz.htzcosmetics.client;

import java.util.UUID;

/** Added to the player render state by a mixin, so the render layer knows whose cosmetics to draw. */
public interface PlayerUuidAccess {
    UUID htzcosmetics$getUuid();

    void htzcosmetics$setUuid(UUID uuid);
}
