package dev.flowicons.jetbrains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlowIconsIconPackTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesCurrentVsixAndZedArchivePaths() {
        assertEquals("deep/go.svg", FlowIconsIconPack.normalizeArchiveIconPath("extension/deep/go.svg"));
        assertEquals("you/go.svg", FlowIconsIconPack.normalizeArchiveIconPath("extension/you/go.svg"));
        assertEquals("you-light/go.svg", FlowIconsIconPack.normalizeArchiveIconPath("extension/you-light/go.svg"));
        assertEquals("icons/go.svg", FlowIconsIconPack.normalizeArchiveIconPath("extension/icons/go.svg"));
        assertEquals("deep/go.svg", FlowIconsIconPack.normalizeArchiveIconPath("flow-icons-zed/icons/deep/go.svg"));
        assertEquals("flow-icons.json", FlowIconsIconPack.normalizeArchiveIconPath("flow-icons-zed/icon_themes/flow-icons.json"));
        assertEquals("you.json", FlowIconsIconPack.normalizeArchiveIconPath("extension/you.json"));
    }

    @Test
    void keepsOnlySafeSupportedArchiveEntries() {
        assertTrue(FlowIconsIconPack.isSafeIconArchiveEntry("deep/go.svg"));
        assertTrue(FlowIconsIconPack.isSafeIconArchiveEntry("deep/go.png"));
        assertTrue(FlowIconsIconPack.isSafeIconArchiveEntry("deep.json"));
        assertTrue(FlowIconsIconPack.isSafeIconArchiveEntry("icons/go.svg"));
    }

    @Test
    void buildsMappingsForEveryFlowPaletteFromVscodeThemeJson() throws IOException {
        Path iconsDir = tempDir.resolve("icons");
        Path mappingsDir = tempDir.resolve("mappings");

        for (String folder : List.of("deep", "deep-light", "dim", "dim-light", "dawn", "dawn-light", "you", "you-light")) {
            writeIcons(iconsDir.resolve(folder));
        }
        Files.writeString(iconsDir.resolve("deep.json"), themeJson());
        Files.writeString(iconsDir.resolve("dim.json"), themeJson());
        Files.writeString(iconsDir.resolve("dawn.json"), themeJson());
        Files.writeString(iconsDir.resolve("you.json"), themeJson());
        Files.writeString(iconsDir.resolve("mapping-overrides.json"), mappingOverridesJson());

        FlowIconsIconPack.buildMappings(iconsDir, mappingsDir);

        for (String folder : List.of("deep", "deep-light", "dim", "dim-light", "dawn", "dawn-light", "you", "you-light")) {
            Path mappingPath = mappingsDir.resolve(folder + ".properties");
            assertTrue(Files.isRegularFile(mappingPath), folder);

            Properties properties = new Properties();
            try (var input = Files.newInputStream(mappingPath)) {
                properties.load(input);
            }

            assertEquals("/flow-icons/icons/" + folder + "/file.svg", properties.getProperty("default.file"));
            assertEquals("/flow-icons/icons/" + folder + "/folder_gray.svg", properties.getProperty("default.directory"));
            assertEquals("/flow-icons/icons/" + folder + "/docker.svg", properties.getProperty("file.stem.dockerfile"));
            assertEquals("go.mod", properties.getProperty("file.native.go.mod"));
            assertEquals("go.mod", properties.getProperty("file.native.go.sum"));
            assertEquals("go.work", properties.getProperty("file.native.go.work"));
            assertEquals("go.work", properties.getProperty("file.native.go.work.sum"));
            assertNull(properties.getProperty("file.stem.go.mod"));
            assertNull(properties.getProperty("file.stem.go.sum"));
            assertEquals("/flow-icons/icons/" + folder + "/go.svg", properties.getProperty("file.tail._test.go"));
            assertEquals("/flow-icons/icons/" + folder + "/go.svg", properties.getProperty("file.suffix.go"));
            assertEquals("/flow-icons/icons/" + folder + "/test_ts.svg", properties.getProperty("file.suffix.test.ts"));
            assertEquals("/flow-icons/icons/" + folder + "/folder_src.svg", properties.getProperty("dir.name.src"));
            assertNotNull(properties.getProperty("file.stem.DOCKERFILE".toLowerCase()));
        }
    }

    private static void writeIcons(Path folder) throws IOException {
        Files.createDirectories(folder);
        for (String icon : List.of("file", "folder_gray", "docker", "go", "test_ts", "folder_src")) {
            Files.writeString(folder.resolve(icon + ".svg"), "<svg viewBox=\"0 0 16 16\"/>");
        }
    }

    private static String themeJson() {
        return """
                {
                  "fileNames": {
                    "Dockerfile": "docker"
                  },
                  "fileExtensions": {
                    "go": "go",
                    "test.ts": "test_ts"
                  },
                  "folderNames": {
                    "src": "folder_src"
                  }
                }
                """;
    }

    private static String mappingOverridesJson() {
        return """
                {
                  "fileNames": {
                  },
                  "nativeFileNames": {
                    "go.mod": "go.mod",
                    "go.sum": "go.mod",
                    "go.work": "go.work",
                    "go.work.sum": "go.work"
                  },
                  "fileGlobs": {
                    "*_test.go": "go"
                  }
                }
                """;
    }
}
