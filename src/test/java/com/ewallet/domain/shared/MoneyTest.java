package com.ewallet.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Executable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money Value Object — Comprehensive TDD & Boundary Specification")
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency KWD = Currency.getInstance("KWD");


    // 1. Construction, Domain Invariants & Architectural Fitness Guards


    @Nested
    @DisplayName("Construction, Factories & Domain Invariants")
    class ConstructionAndInvariantsTests {

        @Test
        @DisplayName("Should create scale-normalized Money via string and minor-unit factories across multi-scale ISO currencies")
        void shouldCreateNormalizedMoneyAcrossCurrencies() {
            Money usdFromString = Money.of("10.5", "USD");
            Money usdFromMinor = Money.ofMinor(1050L, USD);
            Money jpyFromMinor = Money.ofMinor(1500L, JPY);
            Money kwdFromMinor = Money.ofMinor(1250L, KWD);
            Money zeroUsd = Money.zero("USD");

            assertThat(usdFromString.amount()).isEqualByComparingTo("10.50");
            assertThat(usdFromString.amount().scale()).isEqualTo(2);
            assertThat(usdFromString).isEqualTo(usdFromMinor);
            assertThat(usdFromString.toMinorUnits()).isEqualTo(1050L);
            assertThat(usdFromString.toString()).isEqualTo("10.50 USD");

            assertThat(jpyFromMinor.amount().scale()).isZero();
            assertThat(jpyFromMinor.toString()).isEqualTo("1500 JPY");

            assertThat(kwdFromMinor.amount().scale()).isEqualTo(3);
            assertThat(kwdFromMinor.toString()).isEqualTo("1.250 KWD");

            assertThat(zeroUsd.isZero()).isTrue();
            assertThat(zeroUsd.toString()).isEqualTo("0.00 USD");
        }

        @Test
        @DisplayName("Should avoid IEEE 754 binary corruption when parsing 0.1 + 0.2")
        void shouldAvoidFloatingPointCorruption() {
            Money tenCents = Money.of("0.1", USD);
            Money twentyCents = Money.of("0.2", USD);

            Money sum = tenCents.add(twentyCents);

            assertThat(sum).isEqualTo(Money.of("0.30", USD));
            assertThat(sum.amount().toPlainString()).isEqualTo("0.30");
        }

        @Test
        @DisplayName("Should reject null amount or null currency at construction time (Fail-Fast)")
        void shouldRejectNullArguments() {
            assertThatThrownBy(() -> new Money(null, USD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Monetary amount must not be null");

            assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Currency must not be null");

            assertThatThrownBy(() -> Money.of((String) null, USD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Monetary amount string must not be null");

            assertThatThrownBy(() -> Money.of("10.00", (String) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Currency code must not be null");
        }

        @ParameterizedTest(name = "Invalid amount string \"{0}\" must be rejected")
        @ValueSource(strings = {"", "   ", " 10.00", "10.00 ", "abc", "10,50", "$10.00"})
        @DisplayName("Should reject blank, whitespace-padded, or malformed decimal strings")
        void shouldRejectInvalidAmountStrings(String invalidAmount) {
            assertThatThrownBy(() -> Money.of(invalidAmount, USD))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should preserve NumberFormatException as root cause for malformed numeric strings")
        void shouldPreserveRootCauseForMalformedNumber() {
            assertThatThrownBy(() -> Money.of("12.34.56", USD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid decimal format for Monetary amount")
                    .hasMessageContaining("12.34.56")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }

        @ParameterizedTest(name = "Non-monetary ISO code \"{0}\" with negative fraction digits must be rejected")
        @ValueSource(strings = {"XXX", "XAU", "XAG"})
        @DisplayName("Should reject non-monetary ISO-4217 codes lacking standard fraction digits")
        void shouldRejectNonMonetaryIsoCodes(String nonMonetaryCode) {
            Currency nonMonetary = Currency.getInstance(nonMonetaryCode);

            assertThatThrownBy(() -> new Money(BigDecimal.TEN, nonMonetary))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported non-monetary currency")
                    .hasMessageContaining(nonMonetaryCode);

            assertThatThrownBy(() -> Money.ofMinor(100L, nonMonetary))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(nonMonetaryCode);
        }

        @ParameterizedTest(name = "Invalid ISO currency code \"{0}\" must be rejected")
        @ValueSource(strings = {"", "   ", "US", "INVALID", "usd"})
        @DisplayName("Should reject invalid or blank ISO-4217 currency codes")
        void shouldRejectInvalidCurrencyCodes(String invalidCode) {
            assertThatThrownBy(() -> Money.of("10.00", invalidCode))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Architectural Fitness Guard: Money must NEVER expose double or float parameters")
        void shouldNeverExposeFloatingPointParameters() {
            List<Executable> allExecutables = new ArrayList<>();
            allExecutables.addAll(Arrays.asList(Money.class.getDeclaredConstructors()));
            allExecutables.addAll(Arrays.asList(Money.class.getDeclaredMethods()));

            Set<Class<?>> forbiddenTypes = Set.of(double.class, Double.class, float.class, Float.class);

            for (Executable executable : allExecutables) {
                for (Class<?> paramType : executable.getParameterTypes()) {
                    assertThat(paramType)
                            .as("Floating-point parameter forbidden in %s", executable.getName())
                            .isNotIn(forbiddenTypes);
                }
            }
        }
    }


    // 2. Banker's Rounding (HALF_EVEN) Parametric Boundary Verification


    @Nested
    @DisplayName("Banker's Rounding (HALF_EVEN) Boundary Behavior")
    class BankersRoundingTests {

        @ParameterizedTest(name = "[{index}] {0} {1} -> {2} {1} (HALF_EVEN)")
        @CsvSource({
                // Positive exact midpoints (USD: scale 2) — round to nearest EVEN digit
                "1.005,  USD, 1.00",
                "1.015,  USD, 1.02",
                "1.025,  USD, 1.02",
                "1.035,  USD, 1.04",
                "1.045,  USD, 1.04",
                "1.055,  USD, 1.06",
                // Strictly above and below midpoint (non-ties round normally)
                "1.0051, USD, 1.01",
                "1.0049, USD, 1.00",
                "1.02500001, USD, 1.03",
                // Negative exact midpoints — symmetric round to nearest EVEN digit
                "-1.005, USD, -1.00",
                "-1.015, USD, -1.02",
                "-1.025, USD, -1.02",
                "-1.035, USD, -1.04",
                "-1.0051, USD, -1.01",
                // Zero-decimal currency (JPY: scale 0)
                "0.5,    JPY, 0",
                "1.5,    JPY, 2",
                "2.5,    JPY, 2",
                "3.5,    JPY, 4",
                // Three-decimal currency (KWD: scale 3)
                "1.0005, KWD, 1.000",
                "1.0015, KWD, 1.002",
                "1.0025, KWD, 1.002"
        })
        @DisplayName("Should apply HALF_EVEN rounding accurately across all midpoint and multi-currency boundaries")
        void shouldApplyBankersRoundingAcrossBoundaries(String rawInput, String currencyCode, String expected) {
            Money actual = Money.of(rawInput, currencyCode);

            assertThat(actual.amount().toPlainString()).isEqualTo(expected);
            assertThat(actual.currency().getCurrencyCode()).isEqualTo(currencyCode);
        }

        @Test
        @DisplayName("Should neutralize cumulative statistical bias across symmetric midpoint transactions")
        void shouldEliminateStatisticalBiasAcrossSymmetricMidpoints() {
            List<String> midpoints = List.of("1.005", "1.015", "1.025", "1.035");

            Money sumWithBankersRounding = midpoints.stream()
                    .map(raw -> Money.of(raw, USD))
                    .reduce(Money.zero(USD), Money::add);

            // Exact mathematical sum of 1.005 + 1.015 + 1.025 + 1.035 = 4.080 (zero cumulative drift)
            assertThat(sumWithBankersRounding).isEqualTo(Money.of("4.08", USD));
        }
    }


    // 3. Side Effect Free Arithmetic & Currency Safety Guards


    @Nested
    @DisplayName("Side-Effect Free Arithmetic & Currency Guards")
    class SideEffectFreeArithmeticTests {

        @Test
        @DisplayName("Should add and subtract same-currency Money without mutating original operands")
        void shouldAddAndSubtractWithoutSideEffects() {
            Money originalBalance = Money.of("100.00", USD);
            Money deposit = Money.of("25.75", USD);

            Money afterDeposit = originalBalance.add(deposit);
            Money afterWithdrawal = afterDeposit.subtract(Money.of("40.25", USD));

            assertThat(afterDeposit).isEqualTo(Money.of("125.75", USD));
            assertThat(afterWithdrawal).isEqualTo(Money.of("85.50", USD));

            // Prove strict immutability (zero side effects on original operands)
            assertThat(originalBalance).isEqualTo(Money.of("100.00", USD));
            assertThat(deposit).isEqualTo(Money.of("25.75", USD));
            assertThat(afterDeposit).isNotSameAs(originalBalance);
        }

        @Test
        @DisplayName("Should throw detailed IllegalArgumentException on currency mismatch in add, subtract, and compareTo")
        void shouldRejectCurrencyMismatchWithClearDiagnostics() {
            Money usd = Money.of("50.00", USD);
            Money eur = Money.of("50.00", EUR);

            assertThatThrownBy(() -> usd.add(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch")
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");

            assertThatThrownBy(() -> usd.subtract(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch")
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");

            assertThatThrownBy(() -> usd.compareTo(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        @DisplayName("Should respect custom RoundingMode in multiply BEFORE Compact Constructor executes")
        void shouldRespectCustomRoundingModeInMultiply() {
            Money principal = Money.of("10.00", USD);

            // 10.00 * 0.0011 = 0.0110 -> CEILING must yield 0.02, while HALF_EVEN yields 0.01
            Money taxCeiling = principal.multiply("0.0011", RoundingMode.CEILING);
            Money feeBankers = principal.multiply("0.0011", RoundingMode.HALF_EVEN);
            Money feeDefault = principal.multiply("0.0011");
            Money discountFloor = principal.multiply("0.0019", RoundingMode.FLOOR);

            assertThat(taxCeiling).isEqualTo(Money.of("0.02", USD));
            assertThat(feeBankers).isEqualTo(Money.of("0.01", USD));
            assertThat(feeDefault).isEqualTo(Money.of("0.01", USD));
            assertThat(discountFloor).isEqualTo(Money.of("0.01", USD));
            assertThat(principal).isEqualTo(Money.of("10.00", USD));
        }

        @Test
        @DisplayName("Should divide safely even on non-terminating decimal expansions (100 / 3, 100 / 7)")
        void shouldDivideSafelyOnNonTerminatingExpansions() {
            Money hundred = Money.of("100.00", USD);

            Money dividedByThree = hundred.divide(3);
            Money dividedByThreeUp = hundred.divide(3, RoundingMode.UP);
            Money dividedBySeven = hundred.divide(new BigDecimal("7"));

            assertThat(dividedByThree).isEqualTo(Money.of("33.33", USD));
            assertThat(dividedByThreeUp).isEqualTo(Money.of("33.34", USD));
            assertThat(dividedBySeven).isEqualTo(Money.of("14.29", USD));
        }

        @ParameterizedTest(name = "Dividing by zero representation \"{0}\" must throw ArithmeticException")
        @ValueSource(strings = {"0", "0.0", "0.00", "-0.000"})
        @DisplayName("Should fail fast with ArithmeticException when dividing by any scaled zero representation")
        void shouldRejectDivisionByAnyScaledZero(String zeroRepresentation) {
            Money hundred = Money.of("100.00", USD);
            BigDecimal scaledZero = new BigDecimal(zeroRepresentation);

            assertThatThrownBy(() -> hundred.divide(scaledZero))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Cannot divide monetary amount by zero");
        }

        @Test
        @DisplayName("Should support sign predicates, negate, and abs without mutation")
        void shouldSupportSignOperationsAndPredicates() {
            Money negative = Money.of("-42.50", USD);
            Money positive = negative.abs();
            Money negatedAgain = positive.negate();

            assertThat(negative.isNegative()).isTrue();
            assertThat(negative.isPositive()).isFalse();
            assertThat(negative.isZero()).isFalse();

            assertThat(positive).isEqualTo(Money.of("42.50", USD));
            assertThat(positive.isPositive()).isTrue();
            assertThat(negatedAgain).isEqualTo(negative);
        }
    }


    // 4. Penny Allocation Engine (Conservation of Money)


    @Nested
    @DisplayName("Penny Allocation & Conservation of Money")
    class PennyAllocationTests {

        @Test
        @DisplayName("Should split 100.00 USD across 3 equal shares without losing a single cent")
        void shouldAllocateEqualSharesPreservingEveryCent() {
            Money total = Money.of("100.00", USD);

            Money[] shares = total.allocate(3);

            assertThat(shares).containsExactly(
                    Money.of("33.34", USD),
                    Money.of("33.33", USD),
                    Money.of("33.33", USD)
            );

            Money reconstructedSum = Arrays.stream(shares).reduce(Money.zero(USD), Money::add);
            assertThat(reconstructedSum).isEqualTo(total);
        }

        @Test
        @DisplayName("Should split negative amounts deterministically while preserving Conservation of Money")
        void shouldAllocateNegativeAmountDeterministically() {
            Money refund = Money.of("-100.00", USD);

            Money[] shares = refund.allocate(3);

            assertThat(shares).containsExactly(
                    Money.of("-33.34", USD),
                    Money.of("-33.33", USD),
                    Money.of("-33.33", USD)
            );

            Money reconstructedSum = Arrays.stream(shares).reduce(Money.zero(USD), Money::add);
            assertThat(reconstructedSum).isEqualTo(refund);
        }

        @Test
        @DisplayName("Should allocate proportionally by ratios and skip zero-ratio buckets for remainders")
        void shouldAllocateByRatiosAndSkipZeroRatioBuckets() {
            Money feePool = Money.of("0.05", USD);

            Money[] twoWay = feePool.allocate(70, 30);
            Money[] withZeroBucket = feePool.allocate(0, 70, 30);

            assertThat(twoWay).containsExactly(
                    Money.of("0.04", USD),
                    Money.of("0.01", USD)
            );

            assertThat(withZeroBucket).containsExactly(
                    Money.of("0.00", USD),
                    Money.of("0.04", USD),
                    Money.of("0.01", USD)
            );
        }

        @Test
        @DisplayName("Should prevent silent long overflow when allocating massive amounts with huge ratios")
        void shouldPreventLongOverflowDuringLargeRatioAllocation() {
            // 10^12 minor units * 10^9 ratio = 10^21 (exceeds Long.MAX_VALUE ~9.22 * 10^18)
            Money massivePool = Money.of("10000000000.00", USD);

            Money[] shares = massivePool.allocate(1_000_000_000L, 1_000_000_000L);

            assertThat(shares).containsExactly(
                    Money.of("5000000000.00", USD),
                    Money.of("5000000000.00", USD)
            );
        }

        @ParameterizedTest(name = "Conserving every cent for {0} {1} split into {2} parts")
        @CsvSource({
                "100.00,  USD, 3",
                "100.00,  USD, 7",
                "0.01,    USD, 5",
                "999.99,  USD, 13",
                "-50.00,  USD, 6",
                "1000,    JPY, 3",
                "10.000,  KWD, 7"
        })
        @DisplayName("Property Check: Sum of equal allocations must ALWAYS equal original amount and differ by at most 1 minor unit")
        void shouldMaintainConservationAndFairnessInvariants(String amount, String currencyCode, int parts) {
            Money original = Money.of(amount, currencyCode);

            Money[] shares = original.allocate(parts);

            Money sum = Arrays.stream(shares).reduce(Money.zero(currencyCode), Money::add);
            assertThat(sum).isEqualTo(original);

            long minMinor = Arrays.stream(shares).mapToLong(Money::toMinorUnits).min().orElseThrow();
            long maxMinor = Arrays.stream(shares).mapToLong(Money::toMinorUnits).max().orElseThrow();
            assertThat(maxMinor - minMinor).isLessThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("Should reject invalid allocation parts and ratios (Fail-Fast)")
        void shouldRejectInvalidAllocationInputs() {
            Money amount = Money.of("10.00", USD);

            assertThatThrownBy(() -> amount.allocate(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("strictly positive");

            assertThatThrownBy(() -> amount.allocate(-2))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> amount.allocate(new long[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one allocation ratio");

            assertThatThrownBy(() -> amount.allocate(50, -10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");

            assertThatThrownBy(() -> amount.allocate(0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sum of allocation ratios must be strictly positive");
        }
    }


    // 5. BigDecimal Equality Trap Resolution & Collection Contracts


    @Nested
    @DisplayName("Equality, HashCode & Collection Contracts (HashMap / HashSet / TreeMap)")
    class EqualityAndCollectionContractTests {

        @Test
        @DisplayName("Should resolve BigDecimal equals/hashCode scale trap across 10, 10.0, 10.00, and 10.000")
        void shouldResolveBigDecimalScaleTrap() {
            Money m0 = Money.of("10", USD);
            Money m1 = Money.of("10.0", USD);
            Money m2 = Money.of("10.00", USD);
            Money m3 = Money.of("10.000", USD);
            Money mMinor = Money.ofMinor(1000L, USD);

            assertThat(m0).isEqualTo(m1).isEqualTo(m2).isEqualTo(m3).isEqualTo(mMinor);
            assertThat(m0.hashCode())
                    .isEqualTo(m1.hashCode())
                    .isEqualTo(m2.hashCode())
                    .isEqualTo(m3.hashCode())
                    .isEqualTo(mMinor.hashCode());
            assertThat(m0.compareTo(m2)).isZero();
        }

        @Test
        @DisplayName("Should retrieve keys reliably in HashMap, HashSet, and TreeMap regardless of input string scale")
        void shouldBehaveConsistentlyInHashAndSortedCollections() {
            Money keyPut = Money.of("10.0", USD);
            Money keyLookup = Money.of("10.00", USD);
            Money keyLookupMinor = Money.ofMinor(1000L, USD);

            Map<Money, String> hashMap = new HashMap<>();
            hashMap.put(keyPut, "STANDARD_TIER");

            assertThat(hashMap.get(keyLookup)).isEqualTo("STANDARD_TIER");
            assertThat(hashMap.get(keyLookupMinor)).isEqualTo("STANDARD_TIER");

            Set<Money> hashSet = new HashSet<>();
            hashSet.add(keyPut);
            hashSet.add(keyLookup);
            hashSet.add(keyLookupMinor);
            assertThat(hashSet).hasSize(1);

            Map<Money, String> treeMap = new TreeMap<>();
            treeMap.put(keyPut, "STANDARD_TIER");
            assertThat(treeMap.get(keyLookup)).isEqualTo("STANDARD_TIER");
        }

        @Test
        @DisplayName("Should distinguish identical numeric amounts with different currencies")
        void shouldNotEqualDifferentCurrencies() {
            Money tenUsd = Money.of("10.00", USD);
            Money tenEur = Money.of("10.00", EUR);

            assertThat(tenUsd).isNotEqualTo(tenEur);
        }

        @Test
        @DisplayName("Should evaluate fluent comparison predicates accurately")
        void shouldEvaluateComparisonPredicates() {
            Money lower = Money.of("10.00", USD);
            Money equal = Money.of("10.0", USD);
            Money higher = Money.of("20.00", USD);

            assertThat(higher.isGreaterThan(lower)).isTrue();
            assertThat(lower.isGreaterThan(equal)).isFalse();
            assertThat(lower.isGreaterThanOrEqual(equal)).isTrue();
            assertThat(lower.isLessThan(higher)).isTrue();
            assertThat(lower.isLessThanOrEqual(equal)).isTrue();
        }
    }
}