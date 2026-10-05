package shop.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Markdown rules for stock that is close to its expiration date.
 * Items stay sellable through their expiration day and are never sold afterwards.
 */
public final class PricingPolicy {
    private PricingPolicy() {}

    public static boolean isExpired(LocalDate exp, LocalDate today) {
        return exp != null && exp.isBefore(today);
    }

    /** Days left until expiry, or {@code null} for products that never expire. */
    public static Long daysLeft(LocalDate exp, LocalDate today) {
        return exp == null ? null : ChronoUnit.DAYS.between(today, exp);
    }

    public static int discountPercent(LocalDate exp, LocalDate today) {
        if (exp == null) return 0;
        long days = ChronoUnit.DAYS.between(today, exp);
        if (days < 0) return 0;
        if (days <= 1) return 50;
        if (days <= 3) return 30;
        if (days <= 7) return 15;
        return 0;
    }

    /** Price after the expiry markdown, rounded half up to the nearest cent. */
    public static long price(long listPriceCents, LocalDate exp, LocalDate today) {
        int pct = discountPercent(exp, today);
        return (listPriceCents * (100 - pct) + 50) / 100;
    }
}
