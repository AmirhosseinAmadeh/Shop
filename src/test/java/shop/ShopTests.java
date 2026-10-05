package shop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import shop.model.Order;
import shop.service.Persistence;
import shop.service.PricingPolicy;
import shop.service.Shop;
import shop.service.ShopException;
import shop.util.Json;
import shop.web.ApiServer;

/** Dependency-free test runner: {@code java -cp out shop.ShopTests} exits non-zero on failure. */
public final class ShopTests {
    private static int checks = 0;
    private static int failures = 0;

    static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    static Shop newShop() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new Shop(clock);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.err.println("FAIL: " + what);
        }
    }

    interface Thrower { void run() throws Exception; }

    static void throwsShop(Thrower t, String what) {
        checks++;
        try {
            t.run();
        } catch (ShopException e) {
            return;
        } catch (Exception e) {
            failures++;
            System.err.println("FAIL: " + what + " threw " + e);
            return;
        }
        failures++;
        System.err.println("FAIL: " + what + " did not throw");
    }

    static void testPricing() {
        check(PricingPolicy.discountPercent(null, TODAY) == 0, "no expiry, no discount");
        check(PricingPolicy.discountPercent(TODAY.plusDays(30), TODAY) == 0, "fresh stock is full price");
        check(PricingPolicy.discountPercent(TODAY.plusDays(7), TODAY) == 15, "7 days left -> 15%");
        check(PricingPolicy.discountPercent(TODAY.plusDays(3), TODAY) == 30, "3 days left -> 30%");
        check(PricingPolicy.discountPercent(TODAY.plusDays(1), TODAY) == 50, "1 day left -> 50%");
        check(PricingPolicy.discountPercent(TODAY, TODAY) == 50, "expires today -> 50%");
        check(!PricingPolicy.isExpired(TODAY, TODAY), "still sellable on the expiration day");
        check(PricingPolicy.isExpired(TODAY.minusDays(1), TODAY), "expired the day after");
        check(PricingPolicy.price(189, TODAY.plusDays(1), TODAY) == 95, "189 at 50% rounds half up to 95");
        check(PricingPolicy.price(100, TODAY.plusDays(3), TODAY) == 70, "100 at 30% -> 70");
    }

    static void testCatalogAndStock() {
        Shop s = newShop();
        s.addProduct("MLK", "Milk", "Dairy", 200);
        throwsShop(() -> s.addProduct("MLK", "Dup", "Dairy", 100), "duplicate SKU");
        throwsShop(() -> s.addProduct("X", "", "Dairy", 100), "blank name");
        throwsShop(() -> s.addProduct("X", "Thing", "Dairy", 0), "zero price");
        throwsShop(() -> s.receive("NOPE", 1, null, null), "unknown SKU");
        throwsShop(() -> s.receive("MLK", 5, TODAY.minusDays(9), TODAY.minusDays(1)), "expired batch rejected");
        throwsShop(() -> s.receive("MLK", 5, TODAY, TODAY.minusDays(2)), "exp before mfg");
        throwsShop(() -> s.receive("MLK", 0, null, null), "zero quantity");

        s.receive("MLK", 10, TODAY.minusDays(2), TODAY.plusDays(20));
        s.receive("MLK", 4, TODAY.minusDays(8), TODAY.plusDays(2));
        Shop.ProductView v = s.product("MLK");
        check(v.stock() == 14, "stock sums both batches");
        check(v.nearestExp().equals(TODAY.plusDays(2)), "nearest expiry is the older batch");
        check(v.discountPercent() == 30 && v.currentPriceCents() == 140, "current price follows the FEFO batch");
    }

    static void testCheckoutFefo() {
        Shop s = newShop();
        s.addProduct("MLK", "Milk", "Dairy", 200);
        s.receive("MLK", 10, null, TODAY.plusDays(20));  // b1: full price
        s.receive("MLK", 4, null, TODAY.plusDays(1));    // b2: 50% off, sold first

        Order o = s.checkout(List.of(new Order.Request("MLK", 6)));
        check(o.lines().size() == 2, "order splits across batches");
        check(o.lines().get(0).batchId().equals("B2") && o.lines().get(0).qty() == 4, "earliest expiry sold first");
        check(o.lines().get(0).unitPriceCents() == 100, "discounted batch at 50%");
        check(o.lines().get(1).batchId().equals("B1") && o.lines().get(1).qty() == 2, "remainder from fresh batch");
        check(o.totalCents() == 4 * 100 + 2 * 200, "order total");
        check(o.savedCents() == 400, "savings from the markdown");
        check(s.product("MLK").stock() == 8, "stock reduced");
        check(s.product("MLK").batches().size() == 1, "empty batch removed");
    }

    static void testCheckoutIsAtomic() {
        Shop s = newShop();
        s.addProduct("A", "Apples", "Produce", 100);
        s.addProduct("B", "Bread", "Bakery", 300);
        s.receive("A", 5, null, null);
        s.receive("B", 1, null, null);

        throwsShop(() -> s.checkout(List.of(new Order.Request("A", 3), new Order.Request("B", 2))),
                "insufficient stock for second line");
        check(s.product("A").stock() == 5 && s.product("B").stock() == 1, "nothing was deducted on failure");
        check(s.orders().isEmpty(), "no order recorded on failure");
        throwsShop(() -> s.checkout(List.of()), "empty cart");
        throwsShop(() -> s.checkout(List.of(new Order.Request("A", -1))), "negative quantity");
        throwsShop(() -> s.checkout(List.of(new Order.Request("ZZZ", 1))), "unknown product");

        Order o = s.checkout(List.of(new Order.Request("A", 2), new Order.Request("A", 3)));  // merged duplicates
        check(o.lines().stream().mapToInt(Order.Line::qty).sum() == 5, "duplicate lines are merged");
        check(s.product("A").stock() == 0, "sold out");
    }

    static void testExpiredStockAndWriteOff() {
        // A clock we can move forward to age the stock.
        Clock[] now = {Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)};
        Clock moving = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now[0].instant(); }
        };
        Shop s = new Shop(moving);
        s.addProduct("YOG", "Yogurt", "Dairy", 300);
        s.receive("YOG", 6, null, TODAY.plusDays(2));
        s.receive("YOG", 4, null, TODAY.plusDays(30));

        now[0] = Clock.fixed(TODAY.plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        check(s.product("YOG").stock() == 4, "expired batch is not sellable");
        check(s.alerts(7).size() == 1 && s.alerts(7).get(0).expired(), "expired batch shows up in alerts");
        throwsShop(() -> s.checkout(List.of(new Order.Request("YOG", 5))), "cannot sell expired units");

        long lost = s.writeOffExpired();
        check(lost == 6 * 300, "write-off valued at list price");
        check(s.product("YOG").batches().size() == 1, "expired batch removed");
        check(s.report().writtenOffCents() == 1800, "write-off recorded in the report");
        check(s.writeOffExpired() == 0, "second write-off is a no-op");
    }

    static void testReportAndPersistence() throws Exception {
        Shop s = newShop();
        s.addProduct("MLK", "Milk", "Dairy", 200);
        s.addProduct("RIC", "Rice", "Pantry", 700);
        s.receive("MLK", 10, null, TODAY.plusDays(1));
        s.receive("RIC", 10, TODAY.minusDays(30), TODAY.plusMonths(12));
        s.checkout(List.of(new Order.Request("MLK", 3), new Order.Request("RIC", 1)));

        Shop.Report r = s.report();
        check(r.orders() == 1 && r.unitsSold() == 4, "report counts");
        check(r.revenueCents() == 3 * 100 + 700, "report revenue");
        check(r.savedCents() == 300, "report savings");
        check(r.revenueByCategory().get("Dairy") == 300 && r.revenueByCategory().get("Pantry") == 700, "by category");

        Path f = Files.createTempFile("shop-test", ".json");
        try {
            Persistence.save(s, f);
            Shop loaded = newShop();
            Persistence.load(loaded, f);
            check(loaded.products().size() == 2, "products survive a round trip");
            check(loaded.product("MLK").stock() == 7, "stock survives a round trip");
            check(loaded.orders().size() == 1 && loaded.orders().get(0).totalCents() == 1000, "orders survive");
            check(loaded.product("RIC").batches().get(0).getMFG().equals(TODAY.minusDays(30)), "dates survive");
            loaded.receive("MLK", 1, null, null);
            check(loaded.product("MLK").batches().stream().anyMatch(b -> b.id().equals("B3")), "id counters survive");
        } finally {
            Files.deleteIfExists(f);
        }
    }

    static void testJson() {
        Map<String, Object> m = Json.parseObject("{\"a\":[1,2.5,\"x\\ny\\u0041\"],\"b\":null,\"c\":true,\"d\":{}}");
        check(m.get("b") == null && Boolean.TRUE.equals(m.get("c")), "literals");
        check(((List<?>) m.get("a")).get(0).equals(1L) && ((List<?>) m.get("a")).get(1).equals(2.5), "numbers");
        check(((List<?>) m.get("a")).get(2).equals("x\nyA"), "escapes");
        check(Json.stringify(Json.parse(Json.stringify(m))).equals(Json.stringify(m)), "round trip");
        boolean threw = false;
        try { Json.parse("{\"a\":}"); } catch (Json.JsonException e) { threw = true; }
        check(threw, "malformed JSON is rejected");
    }

    static void testHttpApi() throws Exception {
        Shop s = newShop();
        ApiServer server = new ApiServer(s, "127.0.0.1", 0);
        server.start();
        try {
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> r = send(http, "POST", base + "/api/products",
                    "{\"sku\":\"MLK\",\"name\":\"Milk\",\"category\":\"Dairy\",\"priceCents\":200}");
            check(r.statusCode() == 201, "create product -> 201");
            r = send(http, "POST", base + "/api/products/MLK/batches",
                    "{\"qty\":10,\"exp\":\"" + TODAY.plusDays(1) + "\"}");
            check(r.statusCode() == 201, "receive batch -> 201");
            r = send(http, "GET", base + "/api/products", null);
            check(r.statusCode() == 200 && r.body().contains("\"currentPriceCents\":100"), "list shows markdown price");
            r = send(http, "POST", base + "/api/orders", "{\"items\":[{\"sku\":\"MLK\",\"qty\":4}]}");
            check(r.statusCode() == 201 && r.body().contains("\"totalCents\":400"), "checkout -> 201 with total");
            r = send(http, "POST", base + "/api/orders", "{\"items\":[{\"sku\":\"MLK\",\"qty\":99}]}");
            check(r.statusCode() == 400 && r.body().contains("insufficient stock"), "oversell -> 400");
            r = send(http, "GET", base + "/api/products/NOPE", null);
            check(r.statusCode() == 404, "unknown product -> 404");
            r = send(http, "POST", base + "/api/orders", "{not json");
            check(r.statusCode() == 400, "bad JSON -> 400");
            r = send(http, "GET", base + "/api/report", null);
            check(r.body().contains("\"revenueCents\":400"), "report reflects the sale");
            r = send(http, "GET", base + "/api/nothing", null);
            check(r.statusCode() == 404, "unknown endpoint -> 404");
        } finally {
            server.stop();
        }
    }

    static HttpResponse<String> send(HttpClient http, String method, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {
        testPricing();
        testCatalogAndStock();
        testCheckoutFefo();
        testCheckoutIsAtomic();
        testExpiredStockAndWriteOff();
        testReportAndPersistence();
        testJson();
        testHttpApi();
        System.out.println((checks - failures) + "/" + checks + " checks passed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
