package shop.model;

/** Anything the shop can sell. Prices are in minor units (cents) to avoid floating-point money. */
public interface Product {
    String sku();

    String name();

    String category();

    long priceCents();
}
