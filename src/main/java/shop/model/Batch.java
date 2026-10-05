package shop.model;

import java.time.LocalDate;

/** One received delivery of a product, with its own manufacturing and expiration dates. */
public final class Batch implements MFGDate, EXPDate {
    private final String id;
    private final String sku;
    private int qty;
    private final LocalDate mfg;
    private final LocalDate exp;

    public Batch(String id, String sku, int qty, LocalDate mfg, LocalDate exp) {
        if (qty <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (mfg != null && exp != null && exp.isBefore(mfg))
            throw new IllegalArgumentException("expiration date is before manufacturing date");
        this.id = id;
        this.sku = sku;
        this.qty = qty;
        this.mfg = mfg;
        this.exp = exp;
    }

    public String id() { return id; }

    public String sku() { return sku; }

    public int qty() { return qty; }

    public void remove(int n) {
        if (n > qty) throw new IllegalStateException("removing more than the batch holds");
        qty -= n;
    }

    @Override
    public LocalDate getMFG() { return mfg; }

    @Override
    public LocalDate getEXP() { return exp; }
}
