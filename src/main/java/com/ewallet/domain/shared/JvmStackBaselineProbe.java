package com.ewallet.domain.shared;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Infrastructure baseline probe for verifying Java 21 LTS runtime guarantees,
 * JVM Heap allocation metrics, and live Thread Stack Frame mechanics.
 * <p>
 * Demonstrates domain-centric root packaging and package-private encapsulation
 * for internal helper methods.
 */
public final class JvmStackBaselineProbe {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private JvmStackBaselineProbe() {
        throw new AssertionError("Utility probe class must not be instantiated");
    }

    public static void main(String[] args) {
        System.out.println("=== e-wallet-core | JVM 21 & Stack Frame Baseline ===");
        System.out.printf("Java Runtime Feature Version : %d%n", Runtime.version().feature());
        System.out.printf("Default Charset Encoding     : %s%n", Charset.defaultCharset().name());

        Runtime runtime = Runtime.getRuntime();
        long maxHeapMb = runtime.maxMemory() / BYTES_PER_MEGABYTE;
        long totalHeapMb = runtime.totalMemory() / BYTES_PER_MEGABYTE;
        long freeHeapMb = runtime.freeMemory() / BYTES_PER_MEGABYTE;

        System.out.printf("JVM Heap -> Max: %d MB | Allocated: %d MB | Free: %d MB%n",
                maxHeapMb, totalHeapMb, freeHeapMb);

        System.out.println("\n--- Inspecting Live Stack Frames (LIFO Order) ---");
        List<String> framesAtRoot = captureCurrentCallStack();
        System.out.println("Depth inside main()               : " + framesAtRoot.size());

        List<String> framesAtNestedCall = enterFirstTransactionFrame(10_000L);
        System.out.println("Depth inside nested method calls  : " + framesAtNestedCall.size());
        System.out.println("Active Stack Frames (Top -> Bottom):");
        for (int i = 0; i < framesAtNestedCall.size(); i++) {
            System.out.printf("  Frame[%d]: %s%n", i, framesAtNestedCall.get(i));
        }

        List<String> framesAfterReturn = captureCurrentCallStack();
        System.out.println("Depth after nested calls returned : " + framesAfterReturn.size());
    }

    /**
     * Package-private helper: hidden from outside modules, yet directly testable
     * by unit tests residing in the same package.
     */
    static List<String> enterFirstTransactionFrame(long primitiveAmountMinorUnits) {
        // 'primitiveAmountMinorUnits' lives directly in this frame's Local Variable Array
        long doubledOnOperandStack = primitiveAmountMinorUnits * 2L;
        return enterSecondCalculationFrame(doubledOnOperandStack);
    }

    static List<String> enterSecondCalculationFrame(long computedValue) {
        // 'referenceToHeapString' sits in Local Variable Array, pointing to a String object on Heap
        String referenceToHeapString = "Computed=" + computedValue;
        if (referenceToHeapString.isEmpty()) {
            throw new IllegalStateException("Unexpected empty diagnostic string");
        }
        return captureCurrentCallStack();
    }

    /**
     * Uses Java's modern StackWalker API to inspect live Stack Frames lazily
     * without allocating a heavy Throwable stack trace.
     */
    static List<String> captureCurrentCallStack() {
        return StackWalker.getInstance()
                .walk(stream -> stream
                        .map(frame -> frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber())
                        .toList());
    }
}