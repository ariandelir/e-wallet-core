package com.ewallet.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JVM 21 Runtime & Stack/Heap Memory Baseline Tests")
class JvmBaselineTest {

    @Test
    @DisplayName("Runtime environment must be strictly locked on Java 21 LTS with UTF-8 encoding")
    void shouldRunOnJava21LtsWithUtf8Charset() {
        int javaFeatureVersion = Runtime.version().feature();
        Charset defaultCharset = Charset.defaultCharset();

        assertThat(javaFeatureVersion)
                .as("JVM must execute on Java 21 LTS")
                .isEqualTo(21);

        assertThat(defaultCharset)
                .as("Default JVM charset must be UTF-8 for consistent cross-platform encoding")
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JVM Heap memory must report positive capacity inside the isolated Surefire Forked JVM")
    void shouldReportValidHeapMemoryBaseline() {
        Runtime runtime = Runtime.getRuntime();

        assertThat(runtime.maxMemory()).isPositive();
        assertThat(runtime.totalMemory()).isPositive().isLessThanOrEqualTo(runtime.maxMemory());
        assertThat(runtime.freeMemory()).isNotNegative().isLessThanOrEqualTo(runtime.totalMemory());
    }

    @Test
    @DisplayName("Stack Frames must grow on nested method calls and unwind identically upon return (LIFO)")
    void shouldGrowAndUnwindStackFramesDeterministically() {
        List<String> baselineFrames = JvmStackBaselineProbe.captureCurrentCallStack();
        List<String> nestedFrames = JvmStackBaselineProbe.enterFirstTransactionFrame(5_000L);
        List<String> afterReturnFrames = JvmStackBaselineProbe.captureCurrentCallStack();

        // Entering 2 nested methods pushes exactly 2 additional Stack Frames onto the Thread Stack
        assertThat(nestedFrames)
                .hasSize(baselineFrames.size() + 2);

        // Top frames must prove LIFO execution order: capture -> second -> first
        assertThat(nestedFrames.get(0)).contains("captureCurrentCallStack");
        assertThat(nestedFrames.get(1)).contains("enterSecondCalculationFrame");
        assertThat(nestedFrames.get(2)).contains("enterFirstTransactionFrame");

        // Once nested methods return, their Stack Frames are popped instantly via Pointer Bump
        assertThat(afterReturnFrames)
                .hasSize(baselineFrames.size());
    }
}