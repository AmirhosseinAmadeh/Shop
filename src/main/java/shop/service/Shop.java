package shop.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import shop.model.Batch;
import shop.model.Item;
import shop.model.Order;

/**
 * Catalog, batch-based inventory and checkout.
 *
 * <p>Stock is sold first-expired-first-out (FEFO) and priced per batch through {@link PricingPolicy}.
 * All public methods are synchronized, so one instance can safely back a multi-threaded HTTP server.
 */
public class Shop {
    final Map<String, Item> items = new LinkedHashMap<>();
    final List<Batch> batches = new ArrayList<>();
    final List<Order> orders = new ArrayList<>();
    long nextBatch = 1;
    long nextOrder = 1;
    long writtenOffCents = 0;

    private final Clock clock;
    private Runnable onChange = () -> {};

    public Shop(Clock clock) { this.clock = clock; }

    /** Called after every mutation (the web layer uses it to persist state). */
    public void setOnChange(Runnable r) { this.onChange = r; }

    public LocalDate today() { return LocalDate.now(clock); }

    // ---------------------------------------------------------------- views

    public record ProductView(Item item, int stock, LocalDate nearestExp, long currentPriceCents,
                              int discountPercent, List<Batch> batches) {}

    public record Alert(Item item, Batch batch, Long daysLeft, boolean expired, int discountPercent) {}

    public record Report(int orders, long revenueCents, long savedCents, int unitsSold,
                         long writtenOffCents, Map<String, Long> revenueByCategory) {}

    // ---------------------------------------------------------------- catalog

    public synchronized Item addProduct(String sku, String name, String category, long priceCents) {
        if (sku == null || sku.isBlank()) throw new ShopException("sku is required");
        if (name == null || name.isBlank()) throw new ShopException("name is required");
        if (priceCents <= 0) throw new ShopException("price must be positive");
        if (items.containsKey(sku.trim())) throw new ShopException("SKU already exists: " + sku);
        Item item = new Item(sku.trim(), name.trim(), category == null ? null : category.trim(), priceCents);
        items.put(item.sku(), item);
        onChange.run();
        return item;
    }

    public synchronized Batch receive(String sku, int qty, LocalDate mfg, LocalDate exp) {
        requireItem(sku);
        if (PricingPolicy.isExpired(exp, today())) throw new ShopException("cannot receive an already expired batch");
        Batch b;
        try {
            b = new Batch("B" + nextBatch, sku, qty, mfg, exp);
        } catch (IllegalArgumentException e) {
            throw new ShopException(e.getMessage());
        }
        nextBatch++;
        batches.add(b);
        onChange.run();
        return b;
    }

    public synchronized List<ProductView> products() {
        LocalDate today = today();
        List<ProductView> out = new ArrayList<>();
        for (Item item : items.values()) {
            List<Batch> mine = batchesOf(item.sku());
            List<Batch> sellable = fefo(mine, today);
            int stock = sellable.stream().mapToInt(Batch::qty).sum();
            Batch first = sellable.isEmpty() ? null : sellable.get(0);
            LocalDate exp = first == null ? null : first.getEXP();
            out.add(new ProductView(item, stock, exp,
                    PricingPolicy.price(item.priceCents(), exp, today),
                    PricingPolicy.discountPercent(exp, today), mine));
        }
        return out;
    }

    public synchronized ProductView product(String sku) {
        requireItem(sku);
        return products().stream().filter(p -> p.item().sku().equals(sku)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- expiry

    /** Batches that are expired or expire within {@code withinDays}, soonest first. */
    public synchronized List<Alert> alerts(int withinDays) {
        LocalDate today = today();
        List<Alert> out = new ArrayList<>();
        for (Batch b : batches) {
            if (b.getEXP() == null) continue;
            long days = PricingPolicy.daysLeft(b.getEXP(), today);
            if (days > withinDays) continue;
            out.add(new Alert(items.get(b.sku()), b, days, days < 0, PricingPolicy.discountPercent(b.getEXP(), today)));
        }
        out.sort(Comparator.comparing((Alert a) -> a.batch().getEXP()).thenComparing(a -> a.batch().id()));
        return out;
    }

    /** Removes expired batches from stock and returns their value at list price. */
    public synchronized long writeOffExpired() {
        LocalDate today = today();
        long lost = 0;
        for (var it = batches.iterator(); it.hasNext(); ) {
            Batch b = it.next();
            if (PricingPolicy.isExpired(b.getEXP(), today)) {
                lost += items.get(b.sku()).priceCents() * b.qty();
                it.remove();
            }
        }
        if (lost > 0) {
            writtenOffCents += lost;
            onChange.run();
        }
        return lost;
    }

    // ---------------------------------------------------------------- checkout

    /** Atomic: either every line is fulfilled or nothing changes. */
    public synchronized Order checkout(List<Order.Request> requests) {
        if (requests == null || requests.isEmpty()) throw new ShopException("the cart is empty");
        LocalDate today = today();

        Map<String, Integer> wanted = new TreeMap<>();
        for (Order.Request r : requests) {
            if (r.qty() <= 0) throw new ShopException("quantity must be positive for " + r.sku());
            requireItem(r.sku());
            wanted.merge(r.sku(), r.qty(), Integer::sum);
        }

        record Take(Batch batch, int qty) {}
        List<Take> plan = new ArrayList<>();
        List<Order.Line> lines = new ArrayList<>();
        for (var e : wanted.entrySet()) {
            Item item = items.get(e.getKey());
            int left = e.getValue();
            for (Batch b : fefo(batchesOf(item.sku()), today)) {
                if (left == 0) break;
                int take = Math.min(left, b.qty());
                long unit = PricingPolicy.price(item.priceCents(), b.getEXP(), today);
                lines.add(new Order.Line(item.sku(), item.name(), b.id(), take, unit, item.priceCents(),
                        PricingPolicy.discountPercent(b.getEXP(), today)));
                plan.add(new Take(b, take));
                left -= take;
            }
            if (left > 0)
                throw new ShopException("insufficient stock for " + item.name() + ": requested " + e.getValue()
                        + ", available " + (e.getValue() - left));
        }

        for (Take t : plan) {
            t.batch().remove(t.qty());
            if (t.batch().qty() == 0) batches.remove(t.batch());
        }
        Order order = new Order("O" + nextOrder++, Instant.now(clock).toString(), List.copyOf(lines));
        orders.add(order);
        onChange.run();
        return order;
    }

    public synchronized List<Order> orders() { return List.copyOf(orders); }

    public synchronized Report report() {
        long revenue = 0, saved = 0;
        int units = 0;
        Map<String, Long> byCategory = new TreeMap<>();
        for (Order o : orders) {
            revenue += o.totalCents();
            saved += o.savedCents();
            for (Order.Line l : o.lines()) {
                units += l.qty();
                byCategory.merge(items.get(l.sku()).category(), l.totalCents(), Long::sum);
            }
        }
        return new Report(orders.size(), revenue, saved, units, writtenOffCents, byCategory);
    }

    // ---------------------------------------------------------------- helpers

    private void requireItem(String sku) {
        if (sku == null || !items.containsKey(sku)) throw new ShopException("unknown SKU: " + sku, true);
    }

    private List<Batch> batchesOf(String sku) {
        List<Batch> out = new ArrayList<>();
        for (Batch b : batches) if (b.sku().equals(sku)) out.add(b);
        return out;
    }

    /** Unexpired batches ordered by expiry date (batches that never expire last). */
    private static List<Batch> fefo(List<Batch> in, LocalDate today) {
        List<Batch> out = new ArrayList<>();
        for (Batch b : in) if (!PricingPolicy.isExpired(b.getEXP(), today)) out.add(b);
        out.sort(Comparator.comparing(Batch::getEXP, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Batch::id));
        return out;
    }
}
