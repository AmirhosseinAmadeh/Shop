package shop.model;

import java.time.LocalDate;

/** Something with an expiration date; {@code null} means it never expires. */
public interface EXPDate {
    LocalDate getEXP();
}
