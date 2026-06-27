package dev.flowicons.jetbrains;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FlowIconsThemeMappingTest {
    @Test
    void matchesDirectoryPathRulesBeforeNameRules() {
        Properties properties = new Properties();
        properties.setProperty("default.directory", "default-folder");
        properties.setProperty("dir.name.schema", "schema-folder");
        properties.setProperty("dir.name.prisma/schema", "prisma-schema-folder");

        FlowIconsThemeMapping mapping = FlowIconsThemeMapping.from(properties);

        assertEquals("prisma-schema-folder", mapping.iconPathFor("schema", "C:/project/prisma/schema", true));
        assertEquals("schema-folder", mapping.iconPathFor("schema", "C:/project/schema", true));
        assertEquals("default-folder", mapping.iconPathFor("models", "C:/project/models", true));
    }

    @Test
    void matchesLongestFileSuffixFirst() {
        Properties properties = new Properties();
        properties.setProperty("default.file", "default-file");
        properties.setProperty("file.suffix.ts", "typescript");
        properties.setProperty("file.suffix.test.ts", "typescript-test");

        FlowIconsThemeMapping mapping = FlowIconsThemeMapping.from(properties);

        assertEquals("typescript-test", mapping.iconPathFor("button.test.ts", "C:/project/button.test.ts", false));
        assertEquals("typescript", mapping.iconPathFor("button.ts", "C:/project/button.ts", false));
        assertEquals("default-file", mapping.iconPathFor("button", "C:/project/button", false));
    }

    @Test
    void matchesFileTailRulesBeforeExtensionRules() {
        Properties properties = new Properties();
        properties.setProperty("default.file", "default-file");
        properties.setProperty("file.suffix.go", "go-file");
        properties.setProperty("file.tail._test.go", "go-test-file");

        FlowIconsThemeMapping mapping = FlowIconsThemeMapping.from(properties);

        assertEquals("go-test-file", mapping.iconPathFor("doctor_test.go", "C:/project/doctor_test.go", false));
        assertEquals("go-file", mapping.iconPathFor("doctor.go", "C:/project/doctor.go", false));
    }

    @Test
    void matchesNativeFileAliasesBeforeFlowRules() {
        Properties properties = new Properties();
        properties.setProperty("default.file", "default-file");
        properties.setProperty("file.suffix.sum", "sum-file");
        properties.setProperty("file.native.go.sum", "go.mod");

        FlowIconsThemeMapping mapping = FlowIconsThemeMapping.from(properties);

        assertEquals(FlowIconsThemeMapping.NATIVE_FILE_PREFIX + "go.mod", mapping.iconPathFor("go.sum", "C:/project/go.sum", false));
        assertEquals("sum-file", mapping.iconPathFor("deps.sum", "C:/project/deps.sum", false));
    }
}
