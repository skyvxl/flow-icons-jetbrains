package dev.flowicons.jetbrains;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FlowIconsFileIconProviderTest {
    @Test
    void loadMappingDoesNotApplyBundledOverridesAtRuntime() throws Exception {
        Class<?> packLocationType = Class.forName("dev.flowicons.jetbrains.FlowIconsFileIconProvider$PackLocation");
        Object packLocation = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{packLocationType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "openMapping" -> new ByteArrayInputStream("""
                            default.file=/flow-icons/icons/deep/file.svg
                            file.tail._test.go=/flow-icons/icons/deep/custom_go.svg
                            """.getBytes(StandardCharsets.UTF_8));
                    case "iconPath" -> "/flow-icons/icons/" + args[0] + "/" + args[1] + ".svg";
                    case "loadIcon" -> null;
                    case "cachePrefix" -> "test";
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );

        Method loadMapping = FlowIconsFileIconProvider.class.getDeclaredMethod("loadMapping", packLocationType, String.class);
        loadMapping.setAccessible(true);
        FlowIconsThemeMapping mapping = (FlowIconsThemeMapping) loadMapping.invoke(null, packLocation, "deep");

        assertEquals(
                "/flow-icons/icons/deep/custom_go.svg",
                mapping.iconPathFor("service_test.go", "C:/project/service_test.go", false)
        );
    }
}
