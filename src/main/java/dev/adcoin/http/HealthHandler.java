package dev.adcoin.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * GET /health —— 存活检查（供后端/监控探测，无签名要求，仍受 IP 白名单约束）。
 */
public final class HealthHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    private final ApiContext ctx;

    public HealthHandler(ApiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!ctx.isIpAllowed(ex.getRemoteAddress())) {
                byte[] err = GSON.toJson(Map.of("ok", false, "error", "forbidden"))
                        .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                ex.sendResponseHeaders(403, err.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(err);
                }
                return;
            }
            byte[] ok = GSON.toJson(Map.of(
                    "ok", true,
                    "plugin", "AdCoin",
                    "version", ctx.plugin().getPluginMeta().getVersion()))
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(ok);
            }
        } finally {
            ex.close();
        }
    }
}