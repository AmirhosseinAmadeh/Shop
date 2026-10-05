package shop.service;

/** A business-rule violation (unknown SKU, not enough stock, ...). Maps to HTTP 400/404 in the API. */
public class ShopException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean notFound;

    public ShopException(String message) { this(message, false); }

    public ShopException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public boolean isNotFound() { return notFound; }
}
