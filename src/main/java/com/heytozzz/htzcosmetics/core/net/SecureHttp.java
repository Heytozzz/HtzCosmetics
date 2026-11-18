package com.heytozzz.htzcosmetics.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The mod's only HTTP client.
 * <ul>
 *   <li>HTTPS only, to a fixed whitelist of hosts, default port only, no user-info in URLs.</li>
 *   <li>Redirects are never followed (a redirect is treated as a failure).</li>
 *   <li>Connect and request timeouts; responses larger than the caller's limit are aborted while reading.</li>
 *   <li>Only HTTP 200 is accepted.</li>
 * </ul>
 * Blocking: call it from a background thread, never from the render thread.
 */
public final class SecureHttp {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;
    private final Set<String> allowedHosts;
    private final boolean requireHttps;
    private final String userAgent;

    /** The production client: HTTPS to {@link Endpoints#ALLOWED_HOSTS} only. */
    public static SecureHttp production(String userAgent) {
        return new SecureHttp(Endpoints.ALLOWED_HOSTS, true, userAgent);
    }

    /** Tests only (package-private): allows plain http to a local test server. */
    SecureHttp(Set<String> allowedHosts, boolean requireHttps, String userAgent) {
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.requireHttps = requireHttps;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    /** @throws IOException if the URI is not on the whitelist or not HTTPS */
    public void checkAllowed(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean schemeOk = scheme.equals("https") || (!requireHttps && scheme.equals("http"));
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!schemeOk || !allowedHosts.contains(host) || uri.getRawUserInfo() != null || uri.getRawFragment() != null
            || (requireHttps && uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("blocked URL (not an allowed HTTPS host)");
        }
    }

    public byte[] get(URI uri, int maxBytes) throws IOException {
        return send(HttpRequest.newBuilder(uri).GET(), uri, maxBytes);
    }

    /** POST/PUT/DELETE with an optional JSON body and bearer token (for the backend). */
    public byte[] send(String method, URI uri, byte[] jsonBody, String bearerToken, int maxBytes) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
            .method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(jsonBody));
        if (jsonBody != null) b.header("Content-Type", "application/json");
        if (bearerToken != null) b.header("Authorization", "Bearer " + bearerToken);
        return send(b, uri, maxBytes);
    }

    private byte[] send(HttpRequest.Builder builder, URI uri, int maxBytes) throws IOException {
        checkAllowed(uri);
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).header("User-Agent", userAgent).build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new HttpStatusException(response.statusCode());
            OptionalLong declared = response.headers().firstValueAsLong("Content-Length");
            if (declared.isPresent() && declared.getAsLong() > maxBytes) throw new IOException("response too large");
            return readCapped(body, maxBytes);
        }
    }

    /** Reads at most {@code maxBytes}; more data means the response is rejected (never buffered whole). */
    public static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        byte[] data = in.readNBytes(maxBytes + 1);
        if (data.length > maxBytes) throw new IOException("response too large");
        return data;
    }

    /** A non-200 answer (including redirects). */
    public static final class HttpStatusException extends IOException {
        private final int status;

        public HttpStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
