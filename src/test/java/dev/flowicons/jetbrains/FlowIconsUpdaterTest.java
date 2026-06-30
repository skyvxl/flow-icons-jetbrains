package dev.flowicons.jetbrains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

final class FlowIconsUpdaterTest {
    @TempDir
    Path tempDir;

    private static void copyWithLimit(InputStream input, Path target, long maxBytes, BooleanSupplier canceled) throws Throwable {
        Method method = FlowIconsUpdater.class.getDeclaredMethod(
                "copyWithLimit",
                InputStream.class,
                Path.class,
                long.class,
                BooleanSupplier.class
        );
        method.setAccessible(true);
        try {
            method.invoke(null, input, target, maxBytes, canceled);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void userAgentMatchesPluginVersion() throws Exception {
        Field userAgent = FlowIconsUpdater.class.getDeclaredField("USER_AGENT");
        userAgent.setAccessible(true);

        assertEquals("Flow Icons JetBrains/0.3.0", userAgent.get(null));
    }

    @Test
    void copyWithLimitStopsBeforeWritingWhenCanceled() {
        Path target = tempDir.resolve("download.bin");

        IOException error = assertThrows(
                IOException.class,
                () -> copyWithLimit(new ByteArrayInputStream("abcdef".getBytes()), target, 1024, () -> true)
        );

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("canceled"));
        assertFalse(Files.exists(target));
    }

    @Test
    void copyWithLimitRejectsOversizedDownloads() {
        Path target = tempDir.resolve("download.bin");

        IOException error = assertThrows(
                IOException.class,
                () -> copyWithLimit(new ByteArrayInputStream("abcde".getBytes()), target, 4, () -> false)
        );

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("too large"));
        assertFalse(Files.exists(target));
    }
}
