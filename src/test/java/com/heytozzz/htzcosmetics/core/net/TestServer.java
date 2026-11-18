package com.heytozzz.htzcosmetics.core.net;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Local HTTP server for tests, plus a SecureHttp that is allowed to reach it (plain http, 127.0.0.1 only). */
public final class TestServer implements AutoCloseable {
    public record Reply(int status, byte[] body, Map<String, String> headers, boolean chunked) {
        public static Reply ok(byte[] body) {
            return new Reply(200, body, Map.of(), false);
        }
    }

    private final HttpServer server;
    public final Map<String, Reply> routes = new ConcurrentHashMap<>();
    public final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    public TestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            hits.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            Reply reply = routes.getOrDefault(key, new Reply(404, new byte[0], Map.of(), false));
            reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(reply.status(), reply.chunked() ? 0 : (reply.body().length == 0 ? -1 : reply.body().length));
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply.body());
            }
        });
        server.start();
    }

    public URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    public int hits(String path) {
        AtomicInteger n = hits.get(path);
        return n == null ? 0 : n.get();
    }

    /** A client allowed to reach ONLY this test server over plain http. */
    public SecureHttp client() {
        return new SecureHttp(Set.of("127.0.0.1"), false, "test");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
