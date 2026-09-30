package com.edwinkarolczyk.edhome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Purchase price is for ONE stated unit (1 szt., 1 kg, or 1 l).
 * Shopping quantity is in thousandths; never use floating point money.
 * Null quantity means total cost is unknown, never zero.
 */
final class ShoppingCostRules {
    private ShoppingCostRules() { }

    static Long totalGrosz(Long quantityMilli, long pricePerUnitGrosz) {
        if (pricePerUnitGrosz < 1 || pricePerUnitGrosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowa cena jednostkowa.");
        if (quantityMilli == null) return null;
        if (quantityMilli < 1 || quantityMilli > ShoppingRules.MAX_MILLI)
            throw new IllegalArgumentException("Nieprawidłowa ilość.");
        // Round half up only once, at the level of the displayed receipt sum.
        return BigDecimal.valueOf(pricePerUnitGrosz)
            .multiply(BigDecimal.valueOf(quantityMilli, 3))
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
