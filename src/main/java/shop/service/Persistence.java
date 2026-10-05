package shop.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.model.Batch;
import shop.model.Item;
import shop.model.Order;
import shop.util.Json;

/** Saves and restores a whole {@link Shop} as one JSON file (written atomically). */
public final class Persistence {
    private Persistence() {}

    // Only the shop's own monitor is ever taken (the shop calls save() while already holding it),
    // so there is a single lock order and no deadlock between request threads.
    public static void save(Shop shop, Path file) throws IOException {
        synchronized (shop) {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringify(toMap(shop)), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void load(Shop shop, Path file) throws IOException {
        Map<String, Object> root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        synchronized (shop) {
            fromMap(shop, root);
        }
    }

    // ---------------------------------------------------------------- mapping

    private static Map<String, Object> toMap(Shop shop) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nextBatch", shop.nextBatch);
        root.put("nextOrder", shop.nextOrder);
        root.put("writtenOffCents", shop.writtenOffCents);

        List<Object> items = new ArrayList<>();
        for (Item i : shop.items.values())
            items.add(map("sku", i.sku(), "name", i.name(), "category", i.category(), "priceCents", i.priceCents()));
        root.put("items", items);

        List<Object> batches = new ArrayList<>();
        for (Batch b : shop.batches)
            batches.add(map("id", b.id(), "sku", b.sku(), "qty", b.qty(), "mfg", str(b.getMFG()), "exp", str(b.getEXP())));
        root.put("batches", batches);

        List<Object> orders = new ArrayList<>();
        for (Order o : shop.orders) {
            List<Object> lines = new ArrayList<>();
            for (Order.Line l : o.lines())
                lines.add(map("sku", l.sku(), "name", l.name(), "batchId", l.batchId(), "qty", l.qty(),
                        "unitPriceCents", l.unitPriceCents(), "listPriceCents", l.listPriceCents(),
                        "discountPercent", l.discountPercent()));
            orders.add(map("id", o.id(), "createdAt", o.createdAt(), "lines", lines));
        }
        root.put("orders", orders);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void fromMap(Shop shop, Map<String, Object> root) {
        shop.items.clear();
        shop.batches.clear();
        shop.orders.clear();
        shop.nextBatch = ((Number) root.getOrDefault("nextBatch", 1L)).longValue();
        shop.nextOrder = ((Number) root.getOrDefault("nextOrder", 1L)).longValue();
        shop.writtenOffCents = ((Number) root.getOrDefault("writtenOffCents", 0L)).longValue();

        for (Object o : (List<Object>) root.getOrDefault("items", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            Item i = new Item((String) m.get("sku"), (String) m.get("name"), (String) m.get("category"),
                    ((Number) m.get("priceCents")).longValue());
            shop.items.put(i.sku(), i);
        }
        for (Object o : (List<Object>) root.getOrDefault("batches", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            shop.batches.add(new Batch((String) m.get("id"), (String) m.get("sku"), ((Number) m.get("qty")).intValue(),
                    date(m.get("mfg")), date(m.get("exp"))));
        }
        for (Object o : (List<Object>) root.getOrDefault("orders", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<Order.Line> lines = new ArrayList<>();
            for (Object lo : (List<Object>) m.get("lines")) {
                Map<String, Object> l = (Map<String, Object>) lo;
                lines.add(new Order.Line((String) l.get("sku"), (String) l.get("name"), (String) l.get("batchId"),
                        ((Number) l.get("qty")).intValue(), ((Number) l.get("unitPriceCents")).longValue(),
                        ((Number) l.get("listPriceCents")).longValue(), ((Number) l.get("discountPercent")).intValue()));
            }
            shop.orders.add(new Order((String) m.get("id"), (String) m.get("createdAt"), List.copyOf(lines)));
        }
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String str(LocalDate d) { return d == null ? null : d.toString(); }

    private static LocalDate date(Object o) { return o == null ? null : LocalDate.parse((String) o); }
}
