package shop.model;

import java.util.List;

/** A completed sale. Each line records the batch it was taken from and the price actually charged. */
public record Order(String id, String createdAt, List<Line> lines) {

    public record Line(String sku, String name, String batchId, int qty, long unitPriceCents,
                       long listPriceCents, int discountPercent) {
        public long totalCents() { return unitPriceCents * qty; }

        public long savedCents() { return (listPriceCents - unitPriceCents) * qty; }
    }

    /** What a customer asks for at checkout. */
    public record Request(String sku, int qty) {}

    public long totalCents() { return lines.stream().mapToLong(Line::totalCents).sum(); }

    public long savedCents() { return lines.stream().mapToLong(Line::savedCents).sum(); }
}
