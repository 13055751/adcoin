package dev.adcoin.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * API handler 基类：统一做 IP 白名单 → 请求体解析 → 签名校验 → 防重放，
 * 再派发到子类的业务逻辑。错误统一收敛为 JSON 响应。
 */
public abstract class BaseHandler implements HttpHandler {

    private static final int MAX_BODY = 64 * 1024;

    protected final ApiContext ctx;

    protected BaseHandler(ApiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public final void handle(HttpExchange ex) {
        try {
            if (!ctx.isIpAllowed(ex.getRemoteAddress())) {
                throw new ApiException(403, "forbidden");
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                throw new ApiException(405, "method_not_allowed");
            }
            JsonObject body = parse(readBody(ex));
            if (!ctx.signer().verify(canonical(body), requiredString(body, "sig"))) {
                throw new ApiException(401, "bad_signature");
            }
            long ts = requiredLong(body, "ts");
            if (!ctx.replayGuard().accept(replayId(body), ts)) {
                throw new ApiException(401, "expired");
            }
            handleValid(ex, body);
        } catch (ApiException e) {
            respond(ex, e.status(), error(e.code()));
        } catch (Throwable t) {
            ctx.log().severe("API 内部错误: " + t);
            respond(ex, 500, error("internal"));
        } finally {
            ex.close();
        }
    }

    /** 由子类定义：本接口的 canonical 串（参与签名）。 */
    protected abstract String canonical(JsonObject body);

    /** 由子类定义：防重放用的请求唯一 ID。 */
    protected abstract String replayId(JsonObject body);

    /** 子类业务逻辑（签名/重放已通过）。 */
    protected abstract void handleValid(HttpExchange ex, JsonObject body) throws Exception;

    // ------------------------------------------------------------ 工具

    protected static Map<String, Object> error(String code) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("error", code);
        return m;
    }

    private static final Gson GSON = new Gson();

    protected static void respond(HttpExchange ex, int status, Map<String, Object> json) {
        byte[] bytes = json != null
                ? GSON.toJson(json).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        try {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } catch (IOException e) {
            // 客户端断开，忽略
        }
    }

    protected static JsonObject parse(byte[] body) {
        try {
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ApiException(400, "bad_json");
        }
    }

    protected static String requiredString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new ApiException(400, "missing_field:" + key);
        }
        return o.get(key).getAsString();
    }

    protected static String optionalString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    protected static long requiredLong(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new ApiException(400, "missing_field:" + key);
        }
        try {
            return o.get(key).getAsLong();
        } catch (RuntimeException e) {
            throw new ApiException(400, "bad_field:" + key);
        }
    }

    protected static double requiredDouble(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new ApiException(400, "missing_field:" + key);
        }
        try {
            return o.get(key).getAsDouble();
        } catch (RuntimeException e) {
            throw new ApiException(400, "bad_field:" + key);
        }
    }

    private static byte[] readBody(HttpExchange ex) throws IOException, ApiException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = in.readNBytes(MAX_BODY + 1);
            if (buf.length > MAX_BODY) {
                throw new ApiException(413, "payload_too_large");
            }
            return buf;
        }
    }
}