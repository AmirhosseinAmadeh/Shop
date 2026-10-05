package shop.model;

/** A catalog entry. Stock lives in {@link Batch}es, not here. */
public record Item(String sku, String name, String category, long priceCents) implements Product {
    public Item {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (priceCents <= 0) throw new IllegalArgumentException("price must be positive");
        category = category == null || category.isBlank() ? "General" : category;
    }
}
