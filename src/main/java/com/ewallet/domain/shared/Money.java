package com.ewallet.domain.shared;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable financial Value Object representing a monetary amount bound to an ISO-4217 currency.
 * <p>
 * Domain Invariants guaranteed at construction time:
 * <ul>
 *     <li>Floating-point types ({@code double}, {@code float}) are strictly forbidden.</li>
 *     <li>Both {@code amount} and {@code currency} are strictly non-null.</li>
 *     <li>The currency must support non-negative standard fraction digits (rejecting non-monetary ISO codes like XXX/XAU).</li>
 *     <li>The {@code amount} scale is always aligned to the currency's official fraction digits using Banker's Rounding ({@link RoundingMode#HALF_EVEN}).</li>
 * </ul>
 */
public record Money(BigDecimal amount, Currency currency) {

    /**
     * Enterprise standard rounding algorithm: Banker's Rounding (Round Half to Even)
     * to eliminate cumulative statistical bias across millions of ledger transactions.
     */
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    /**
     * High-precision MathContext (34 significant digits, IEEE 754R Decimal128) with HALF_EVEN
     * reserved exclusively for intermediate non-terminating arithmetic (e.g., divisions),
     * never as a replacement for currency scale alignment.
     */
    public static final MathContext MONETARY_MATH_CONTEXT = new MathContext(34, DEFAULT_ROUNDING_MODE);

    /**
     * Compact Constructor enforcing all domain invariants and scale normalization
     * strictly BEFORE final bytecode field assignment.
     */
    public Money {
        Objects.requireNonNull(amount, "Monetary amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");

        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(
                    "Unsupported non-monetary currency without defined fraction digits: " + currency.getCurrencyCode()
            );
        }

        // Normalize scale to the currency's exact minor units using Banker's Rounding.
        // This guarantees equals() and hashCode() consistency (e.g., 10.5 USD == 10.50 USD).
        amount = amount.setScale(fractionDigits, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Safe static factory creating {@link Money} from an exact decimal string representation.
     * Prevents IEEE 754 binary floating-point corruption by bypassing {@code double} entirely.
     *
     * @param amount   exact decimal string (e.g., "10.50", "-250.75", "1000")
     * @param currency valid ISO-4217 currency
     * @return an immutable, scale-normalized {@link Money} instance
     */
    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount string must not be null");
        if (amount.isBlank()) {
            throw new IllegalArgumentException("Amount string must not be blank");
        }
        if (!amount.equals(amount.trim())) {
            throw new IllegalArgumentException("Amount string must not contain leading or trailing whitespace");
        }

        try {
            BigDecimal parsedAmount = new BigDecimal(amount);
            return new Money(parsedAmount, currency);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal format for monetary amount: '" + amount + "'", ex);
        }
    }

    /**
     * Convenience overload accepting an ISO-4217 currency code (e.g., "USD", "EUR", "JPY").
     */
    public static Money of(String amount, String currencyCode) {
        return of(amount, resolveCurrency(currencyCode));
    }

    /**
     * High-performance static factory creating {@link Money} directly from minor units (e.g., cents).
     * Avoids string parsing overhead and guarantees zero floating-point inaccuracy.
     * <p>
     * Example: {@code Money.ofMinor(1050L, USD)} produces {@code 10.50 USD}.
     * Example: {@code Money.ofMinor(1000L, JPY)} produces {@code 1000 JPY}.
     *
     * @param minorUnits exact monetary value in the currency's smallest subunit
     * @param currency   valid ISO-4217 currency
     * @return an immutable {@link Money} instance
     */
    public static Money ofMinor(long minorUnits, Currency currency) {
        Objects.requireNonNull(currency, "Currency must not be null");

        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(
                    "Unsupported non-monetary currency without defined fraction digits: " + currency.getCurrencyCode()
            );
        }

        BigDecimal exactAmount = BigDecimal.valueOf(minorUnits, fractionDigits);
        return new Money(exactAmount, currency);
    }

    /**
     * Convenience overload creating {@link Money} from minor units and an ISO-4217 currency code.
     */
    public static Money ofMinor(long minorUnits, String currencyCode) {
        return ofMinor(minorUnits, resolveCurrency(currencyCode));
    }

    /**
     * Factory method producing a zero-value {@link Money} aligned to the specified currency's scale.
     */
    public static Money zero(Currency currency) {
        return ofMinor(0L, currency);
    }

    /**
     * Convenience overload producing a zero-value {@link Money} from an ISO-4217 currency code.
     */
    public static Money zero(String currencyCode) {
        return zero(resolveCurrency(currencyCode));
    }

    /**
     * Returns the exact value in minor units (e.g., cents) as a {@code long}.
     * Uses {@link BigDecimal#longValueExact()} to fail fast on overflow instead of silently truncating.
     */
    public long toMinorUnits() {
        return amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
    }

    /**
     * Clean, human-readable financial representation (e.g., "10.50 USD").
     * Avoids scientific notation by using {@link BigDecimal#toPlainString()}.
     */
    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private static Currency resolveCurrency(String currencyCode) {
        Objects.requireNonNull(currencyCode, "Currency code must not be null");
        if (currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid ISO-4217 currency code: '" + currencyCode + "'", ex);
        }
    }
}