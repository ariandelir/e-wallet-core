package com.ewallet.domain.shared;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable financial Value Object representing a monetary amount bound to an ISO-4217 currency.
 * <p>
 * Architectural Guarantees:
 * <ul>
 *     <li>Floating-point types ({@code double}, {@code float}) are strictly forbidden across all entry points.</li>
 *     <li>Strictly immutable and side effect free: all arithmetic operations return brand-new {@link Money} instances,
 *     making this Value Object inherently lock-free thread-safe across concurrent threads.</li>
 *     <li>Scale normalization in the Compact Constructor guarantees 100% consistency across {@code equals()},
 *     {@code hashCode()}, and {@code compareTo()} (eliminating BigDecimal's scale trap in HashMap/TreeMap).</li>
 *     <li>Exact minor-unit Penny Allocation guarantees strict Conservation of Money with zero lost cents.</li>
 * </ul>
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /**
     * Enterprise standard rounding algorithm: Banker's Rounding (Round Half to Even)
     * to eliminate cumulative statistical bias across millions of ledger transactions.
     */
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    /**
     * High-precision MathContext (34 significant digits, IEEE 754R Decimal128) with HALF_EVEN
     * reserved exclusively for intermediate non-terminating arithmetic.
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
        // Because every Money instance of the same currency shares the exact same scale,
        // Java 21's auto-generated record equals() and hashCode() are guaranteed consistent with compareTo().
        amount = amount.setScale(fractionDigits, DEFAULT_ROUNDING_MODE);
    }


    // Static Factory Methods (Safe Entry Points — Double/Float Strictly Banned)


    public static Money of(String amount, Currency currency) {
        BigDecimal parsedAmount = parseDecimal(amount, "Monetary amount");
        return new Money(parsedAmount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return of(amount, resolveCurrency(currencyCode));
    }

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

    public static Money ofMinor(long minorUnits, String currencyCode) {
        return ofMinor(minorUnits, resolveCurrency(currencyCode));
    }

    public static Money zero(Currency currency) {
        return ofMinor(0L, currency);
    }

    public static Money zero(String currencyCode) {
        return zero(resolveCurrency(currencyCode));
    }


    // Side-Effect Free Monetary Arithmetic (Pure Functions — Always New Object)


    /**
     * Adds another {@link Money} value of the exact same currency.
     * Does not mutate either operand; returns a new immutable {@link Money} instance.
     */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another {@link Money} value of the exact same currency.
     * Does not mutate either operand; returns a new immutable {@link Money} instance.
     */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Multiplies this monetary amount by an exact decimal factor (e.g., fee rate or quantity)
     * using an explicit {@link RoundingMode} aligned to the currency's official scale.
     */
    public Money multiply(BigDecimal factor, RoundingMode roundingMode) {
        Objects.requireNonNull(factor, "Multiplication factor must not be null");
        Objects.requireNonNull(roundingMode, "RoundingMode must not be null");

        int fractionDigits = this.currency.getDefaultFractionDigits();
        BigDecimal roundedProduct = this.amount.multiply(factor).setScale(fractionDigits, roundingMode);
        return new Money(roundedProduct, this.currency);
    }

    /**
     * Multiplies this monetary amount by an exact decimal factor using Banker's Rounding ({@link RoundingMode#HALF_EVEN}).
     */
    public Money multiply(BigDecimal factor) {
        return multiply(factor, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Safe string-based multiplication overload (e.g., {@code money.multiply("0.025")})
     * to prevent callers from accidentally passing {@code double} fee rates.
     */
    public Money multiply(String factor, RoundingMode roundingMode) {
        return multiply(parseDecimal(factor, "Multiplication factor"), roundingMode);
    }

    public Money multiply(String factor) {
        return multiply(factor, DEFAULT_ROUNDING_MODE);
    }

    /**
     * Divides this monetary amount by a non-zero decimal divisor with an explicit {@link RoundingMode}.
     * Always aligns directly to the currency's scale to prevent {@code Non-terminating decimal expansion}.
     */
    public Money divide(BigDecimal divisor, RoundingMode roundingMode) {
        Objects.requireNonNull(divisor, "Divisor must not be null");
        Objects.requireNonNull(roundingMode, "RoundingMode must not be null");

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide monetary amount by zero");
        }

        int fractionDigits = this.currency.getDefaultFractionDigits();
        BigDecimal quotient = this.amount.divide(divisor, fractionDigits, roundingMode);
        return new Money(quotient, this.currency);
    }

    /**
     * Divides this monetary amount by a non-zero decimal divisor using Banker's Rounding ({@link RoundingMode#HALF_EVEN}).
     */
    public Money divide(BigDecimal divisor) {
        return divide(divisor, DEFAULT_ROUNDING_MODE);
    }

    public Money divide(long divisor, RoundingMode roundingMode) {
        return divide(BigDecimal.valueOf(divisor), roundingMode);
    }

    public Money divide(long divisor) {
        return divide(BigDecimal.valueOf(divisor), DEFAULT_ROUNDING_MODE);
    }


    // Penny Allocation Engine (Exact Conservation of Money — Zero Cents Lost)


    /**
     * Splits this monetary amount into {@code parts} equal shares without losing or creating
     * a single minor unit (solves the classic Penny Allocation Problem).
     * <p>
     * Example: {@code Money.of("100.00", "USD").allocate(3)} yields {@code [33.34 USD, 33.33 USD, 33.33 USD]}.
     */
    public Money[] allocate(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Allocation parts must be strictly positive, got: " + parts);
        }

        int fractionDigits = this.currency.getDefaultFractionDigits();
        BigInteger totalMinor = this.amount.movePointRight(fractionDigits).toBigIntegerExact();
        BigInteger[] divAndRem = totalMinor.divideAndRemainder(BigInteger.valueOf(parts));

        BigInteger baseShare = divAndRem[0];
        long remainder = divAndRem[1].longValueExact();
        BigInteger step = remainder >= 0 ? BigInteger.ONE : BigInteger.ONE.negate();
        long absRemainder = Math.abs(remainder);

        Money[] allocations = new Money[parts];
        for (int i = 0; i < parts; i++) {
            BigInteger shareMinor = (i < absRemainder) ? baseShare.add(step) : baseShare;
            allocations[i] = new Money(new BigDecimal(shareMinor, fractionDigits), this.currency);
        }
        return allocations;
    }

    /**
     * Splits this monetary amount proportionally according to integer weight {@code ratios}
     * without losing a single minor unit and immune to {@code long} multiplication overflow.
     * <p>
     * Example: {@code Money.of("0.05", "USD").allocate(70, 30)} yields {@code [0.04 USD, 0.01 USD]}.
     */
    public Money[] allocate(long... ratios) {
        Objects.requireNonNull(ratios, "Allocation ratios must not be null");
        if (ratios.length == 0) {
            throw new IllegalArgumentException("At least one allocation ratio must be provided");
        }

        BigInteger totalRatio = BigInteger.ZERO;
        for (long ratio : ratios) {
            if (ratio < 0) {
                throw new IllegalArgumentException("Allocation ratio must not be negative: " + ratio);
            }
            totalRatio = totalRatio.add(BigInteger.valueOf(ratio));
        }
        if (totalRatio.signum() == 0) {
            throw new IllegalArgumentException("Sum of allocation ratios must be strictly positive");
        }

        int fractionDigits = this.currency.getDefaultFractionDigits();
        BigInteger totalMinor = this.amount.movePointRight(fractionDigits).toBigIntegerExact();
        BigInteger remainder = totalMinor;

        BigInteger[] shares = new BigInteger[ratios.length];
        for (int i = 0; i < ratios.length; i++) {
            shares[i] = totalMinor.multiply(BigInteger.valueOf(ratios[i])).divide(totalRatio);
            remainder = remainder.subtract(shares[i]);
        }

        BigInteger step = remainder.signum() >= 0 ? BigInteger.ONE : BigInteger.ONE.negate();
        BigInteger absRemainder = remainder.abs();

        for (int i = 0; absRemainder.signum() > 0; i = (i + 1) % ratios.length) {
            if (ratios[i] > 0) {
                shares[i] = shares[i].add(step);
                absRemainder = absRemainder.subtract(BigInteger.ONE);
            }
        }

        Money[] allocations = new Money[ratios.length];
        for (int i = 0; i < ratios.length; i++) {
            allocations[i] = new Money(new BigDecimal(shares[i], fractionDigits), this.currency);
        }
        return allocations;
    }


    // Sign Operations, Comparison & Predicates (Consistent with equals/hashCode)


    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    public Money abs() {
        return new Money(this.amount.abs(), this.currency);
    }

    public boolean isZero() {
        return this.amount.signum() == 0;
    }

    public boolean isPositive() {
        return this.amount.signum() > 0;
    }

    public boolean isNegative() {
        return this.amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isLessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }

    /**
     * Compares two {@link Money} instances of the exact same currency.
     * Guaranteed 100% consistent with {@link #equals(Object)} because scale is normalized at construction.
     */
    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
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


    // Internal Domain Invariant Guards


    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "Target Money operand must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate on "
                            + this.currency.getCurrencyCode()
                            + " and "
                            + other.currency.getCurrencyCode()
            );
        }
    }

    private static BigDecimal parseDecimal(String rawValue, String fieldLabel) {
        Objects.requireNonNull(rawValue, fieldLabel + " string must not be null");
        if (rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " string must not be blank");
        }
        if (!rawValue.equals(rawValue.trim())) {
            throw new IllegalArgumentException(fieldLabel + " string must not contain leading or trailing whitespace");
        }
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal format for " + fieldLabel + ": '" + rawValue + "'", ex);
        }
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