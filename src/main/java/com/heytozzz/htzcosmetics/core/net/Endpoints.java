package com.heytozzz.htzcosmetics.core.net;

import java.net.URI;
import java.util.Set;

/** The only places the mod ever talks to. Anything else is refused by {@link SecureHttp}. */
public final class Endpoints {
    /** Signed manifest + content-addressed assets (GitHub Pages). */
    public static final URI SITE = URI.create("https://heytozzz.github.io/HtzCosmetics/");
    /** Equipped-cosmetics backend (Cloudflare Worker). */
    public static final URI BACKEND = URI.create("https://htzcosmetics.htzcosmetics-backend.workers.dev/");

    public static final Set<String> ALLOWED_HOSTS = Set.of(SITE.getHost(), BACKEND.getHost());

    private Endpoints() {
    }
}
