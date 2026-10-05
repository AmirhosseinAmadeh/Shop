package shop.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import shop.model.Batch;
import shop.model.Item;
import shop.model.Order;
import shop.service.Shop;
import shop.service.ShopException;
import shop.util.Json;

/** JSON REST API plus the static dashboard, on the JDK's built-in HTTP server. */
public final class ApiServer {
    private final Shop shop;
    private final HttpServer server;

    public ApiServer(Shop shop, String host, int port) throws IOException {
        this.shop = shop;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
    }

    public void start() { server.start(); }

    public void stop() { server.stop(0); }

    public int port() { return server.getAddress().getPort(); }

    // ---------------------------------------------------------------- routing

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                serveDashboard(ex);
                return;
            }
            if (!path.startsWith("/api/")) {
                send(ex, 404, Map.of("error", "not found"));
                return;
            }
            String[] seg = path.substring(5).split("/");
            Object result = route(method, seg, ex);
            if (result == NO_ROUTE) send(ex, 404, Map.of("error", "no such endpoint: " + method + " " + path));
            else send(ex, method.equals("POST") ? 201 : 200, result);
        } catch (ShopException e) {
            send(ex, e.isNotFound() ? 404 : 400, Map.of("error", e.getMessage()));
        } catch (Json.JsonException | ClassCastException | NullPointerException | DateTimeParseException
                 | NumberFormatException e) {
            send(ex, 400, Map.of("error", "invalid request: " + e.getMessage()));
        } catch (RuntimeException e) {
            send(ex, 500, Map.of("error", "internal error"));
        }
    }

    private static final Object NO_ROUTE = new Object();

    private Object route(String method, String[] seg, HttpExchange ex) throws IOException {
        boolean get = method.equals("GET"), post = method.equals("POST");
        switch (seg[0]) {
            case "info":
                if (get) return Map.of("today", shop.today().toString());
                break;
            case "products":
                if (seg.length == 1 && get) return mapList(shop.products(), ApiServer::productJson);
                if (seg.length == 1 && post) {
                    Map<String, Object> b = body(ex);
                    Item i = shop.addProduct((String) b.get("sku"), (String) b.get("name"),
                            (String) b.get("category"), ((Number) b.get("priceCents")).longValue());
                    return productJson(shop.product(i.sku()));
                }
                if (seg.length == 2 && get) return productJson(shop.product(seg[1]));
                if (seg.length == 3 && seg[2].equals("batches") && post) {
                    Map<String, Object> b = body(ex);
                    Batch batch = shop.receive(seg[1], ((Number) b.get("qty")).intValue(),
                            date(b.get("mfg")), date(b.get("exp")));
                    return batchJson(batch);
                }
                break;
            case "alerts":
                if (get) {
                    int days = 7;
                    String q = ex.getRequestURI().getQuery();
                    if (q != null && q.startsWith("days=")) days = Integer.parseInt(q.substring(5));
                    return mapList(shop.alerts(days), ApiServer::alertJson);
                }
                break;
            case "writeoff":
                if (post) return Map.of("writtenOffCents", shop.writeOffExpired());
                break;
            case "orders":
                if (get) return mapList(shop.orders(), ApiServer::orderJson);
                if (post) return orderJson(shop.checkout(requests(body(ex))));
                break;
            case "report":
                if (get) return reportJson(shop.report());
                break;
            default:
        }
        return NO_ROUTE;
    }

    // ---------------------------------------------------------------- JSON views

    private static Map<String, Object> productJson(Shop.ProductView p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", p.item().sku());
        m.put("name", p.item().name());
        m.put("category", p.item().category());
        m.put("priceCents", p.item().priceCents());
        m.put("currentPriceCents", p.currentPriceCents());
        m.put("discountPercent", p.discountPercent());
        m.put("stock", p.stock());
        m.put("nearestExp", p.nearestExp() == null ? null : p.nearestExp().toString());
        m.put("batches", mapList(p.batches(), ApiServer::batchJson));
        return m;
    }

    private static Map<String, Object> batchJson(Batch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id());
        m.put("sku", b.sku());
        m.put("qty", b.qty());
        m.put("mfg", b.getMFG() == null ? null : b.getMFG().toString());
        m.put("exp", b.getEXP() == null ? null : b.getEXP().toString());
        return m;
    }

    private static Map<String, Object> alertJson(Shop.Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", a.item().sku());
        m.put("name", a.item().name());
        m.put("batchId", a.batch().id());
        m.put("qty", a.batch().qty());
        m.put("exp", a.batch().getEXP().toString());
        m.put("daysLeft", a.daysLeft());
        m.put("expired", a.expired());
        m.put("discountPercent", a.discountPercent());
        return m;
    }

    private static Map<String, Object> orderJson(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("createdAt", o.createdAt());
        m.put("totalCents", o.totalCents());
        m.put("savedCents", o.savedCents());
        List<Object> lines = new ArrayList<>();
        for (Order.Line l : o.lines()) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("sku", l.sku());
            lm.put("name", l.name());
            lm.put("batchId", l.batchId());
            lm.put("qty", l.qty());
            lm.put("unitPriceCents", l.unitPriceCents());
            lm.put("discountPercent", l.discountPercent());
            lm.put("totalCents", l.totalCents());
            lines.add(lm);
        }
        m.put("lines", lines);
        return m;
    }

    private static Map<String, Object> reportJson(Shop.Report r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orders", r.orders());
        m.put("revenueCents", r.revenueCents());
        m.put("savedCents", r.savedCents());
        m.put("unitsSold", r.unitsSold());
        m.put("writtenOffCents", r.writtenOffCents());
        m.put("revenueByCategory", r.revenueByCategory());
        return m;
    }

    private static <T> List<Object> mapList(List<T> in, java.util.function.Function<T, Object> f) {
        List<Object> out = new ArrayList<>();
        for (T t : in) out.add(f.apply(t));
        return out;
    }

    // ---------------------------------------------------------------- request helpers

    @SuppressWarnings("unchecked")
    private static List<Order.Request> requests(Map<String, Object> body) {
        List<Order.Request> out = new ArrayList<>();
        for (Object o : (List<Object>) body.get("items")) {
            Map<String, Object> m = (Map<String, Object>) o;
            out.add(new Order.Request((String) m.get("sku"), ((Number) m.get("qty")).intValue()));
        }
        return out;
    }

    private static LocalDate date(Object o) { return o == null ? null : LocalDate.parse((String) o); }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return Json.parseObject(new String(in.readNBytes(1_000_000), StandardCharsets.UTF_8));
        }
    }

    private void serveDashboard(HttpExchange ex) throws IOException {
        try (InputStream in = ApiServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                send(ex, 500, Map.of("error", "dashboard resource missing from classpath"));
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        }
    }

    private static void send(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
}
