package dev.flowicons.jetbrains;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class FlowIconsFileIconProvider implements FileIconProvider {
    private static final String DARK_THEME = "deep";
    private static final String LIGHT_THEME = "deep-light";
    private static final int ICON_SIZE = 18;

    private static final Map<String, FlowIconsThemeMapping> MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> NATIVE_ICON_LOOKUP = ThreadLocal.withInitial(() -> false);

    public static void clearAllCaches() {
        MAPPINGS.clear();
        ICON_CACHE.clear();
    }

    private static String resolveTheme(String configuredTheme) {
        if (!FlowIconsSettings.THEME_AUTO.equals(configuredTheme)) {
            return configuredTheme;
        }
        return UIUtil.isUnderDarcula() ? DARK_THEME : LIGHT_THEME;
    }

    private static @Nullable Icon loadCachedIcon(PackLocation packLocation, FlowIconsSettings settings, String iconPath) {
        String iconKey = packLocation.cachePrefix(settings.getIconPackStamp()) + ":" + iconPath;
        return ICON_CACHE.computeIfAbsent(iconKey, ignored -> packLocation.loadIcon(iconPath));
    }

    private static FlowIconsThemeMapping loadMapping(PackLocation packLocation, String theme) {
        Properties properties = new Properties();
        try (InputStream stream = packLocation.openMapping(theme)) {
            if (stream == null) {
                return FlowIconsThemeMapping.empty();
            }
            properties.load(stream);
            FlowIconsIconPack.applyBundledMappingOverrides(properties, iconId -> packLocation.iconPath(theme, iconId));
        } catch (IOException ignored) {
            return FlowIconsThemeMapping.empty();
        }
        return FlowIconsThemeMapping.from(properties);
    }

    private static @Nullable Icon loadNativeFileIcon(VirtualFile file, String nativeFileName, int flags, @Nullable Project project) {
        if (project == null) {
            return null;
        }

        VirtualFile target = file;
        if (!file.getName().equalsIgnoreCase(nativeFileName)) {
            VirtualFile parent = file.getParent();
            if (parent == null) {
                return null;
            }
            target = parent.findChild(nativeFileName);
            if (target == null) {
                return null;
            }
        }

        boolean previous = NATIVE_ICON_LOOKUP.get();
        NATIVE_ICON_LOOKUP.set(true);
        try {
            return IconUtil.getIcon(target, flags, project);
        } finally {
            if (previous) {
                NATIVE_ICON_LOOKUP.set(true);
            } else {
                NATIVE_ICON_LOOKUP.remove();
            }
        }
    }

    private static @Nullable Icon createIcon(URL url) throws IOException {
        Icon icon = IconLoader.findIcon(url);
        if (icon != null) {
            return icon;
        }
        try (InputStream stream = url.openStream()) {
            return createIcon(stream);
        }
    }

    private static @Nullable Icon createIcon(InputStream stream) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(stream.readAllBytes()));
        return image == null ? null : new FlowImageIcon(cropTransparentPadding(image));
    }

    private static BufferedImage cropTransparentPadding(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return image;
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
        if (NATIVE_ICON_LOOKUP.get()) {
            return null;
        }

        FlowIconsSettings settings = FlowIconsSettings.getInstance();
        PackLocation packLocation = PackLocation.current(settings);
        String theme = resolveTheme(settings.getTheme());

        String mappingKey = packLocation.cachePrefix(settings.getIconPackStamp()) + ":" + theme;
        FlowIconsThemeMapping mapping = MAPPINGS.computeIfAbsent(mappingKey, ignored -> loadMapping(packLocation, theme));
        String iconPath = mapping.iconPathFor(file.getName(), file.getPath(), file.isDirectory());
        if (iconPath == null) {
            return null;
        }

        if (iconPath.startsWith(FlowIconsThemeMapping.NATIVE_FILE_PREFIX)) {
            return loadNativeFileIcon(file, iconPath.substring(FlowIconsThemeMapping.NATIVE_FILE_PREFIX.length()), flags, project);
        }

        return loadCachedIcon(packLocation, settings, iconPath);
    }

    private enum BuiltInPackLocation implements PackLocation {
        INSTANCE;

        @Override
        public @Nullable InputStream openMapping(String theme) {
            return FlowIconsFileIconProvider.class.getResourceAsStream("/flow-icons/mappings/" + theme + ".properties");
        }

        @Override
        public @Nullable Icon loadIcon(String resourcePath) {
            URL url = FlowIconsFileIconProvider.class.getResource(resourcePath);
            if (url == null) {
                return null;
            }
            try {
                return createIcon(url);
            } catch (IOException ignored) {
                return null;
            }
        }

        @Override
        public @Nullable String iconPath(String theme, String iconId) {
            for (String extension : new String[]{".svg", ".png"}) {
                String path = "/flow-icons/icons/" + theme + "/" + iconId + extension;
                if (FlowIconsFileIconProvider.class.getResource(path) != null) {
                    return path;
                }
            }
            return null;
        }

        @Override
        public String cachePrefix(long stamp) {
            return "builtin";
        }
    }

    private interface PackLocation {
        static PackLocation current(FlowIconsSettings settings) {
            Path installedPack = settings.getInstalledPackDir();
            if (settings.hasInstalledPack()) {
                return new FileSystemPackLocation(installedPack);
            }
            return BuiltInPackLocation.INSTANCE;
        }

        @Nullable InputStream openMapping(String theme) throws IOException;

        @Nullable Icon loadIcon(String resourcePath);

        @Nullable String iconPath(String theme, String iconId);

        String cachePrefix(long stamp);
    }

    private record FileSystemPackLocation(Path root) implements PackLocation {

        @Override
        public @Nullable InputStream openMapping(String theme) throws IOException {
            Path mapping = root.resolve("mappings").resolve(theme + ".properties");
            return Files.isRegularFile(mapping) ? Files.newInputStream(mapping) : null;
        }

        @Override
        public @Nullable Icon loadIcon(String resourcePath) {
            String relativePath = resourcePath.replace('\\', '/').replaceFirst("^/flow-icons/", "");
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path iconFile = normalizedRoot.resolve(relativePath.replace('/', File.separatorChar)).normalize();
            if (!iconFile.startsWith(normalizedRoot)) {
                return null;
            }
            try {
                return Files.isRegularFile(iconFile) ? createIcon(iconFile.toUri().toURL()) : null;
            } catch (IOException ignored) {
                return null;
            }
        }

        @Override
        public @Nullable String iconPath(String theme, String iconId) {
            for (String extension : new String[]{".svg", ".png"}) {
                String relativePath = "icons/" + theme + "/" + iconId + extension;
                if (Files.isRegularFile(root.resolve(relativePath.replace('/', File.separatorChar)))) {
                    return "/flow-icons/" + relativePath;
                }
            }
            return null;
        }

        @Override
        public String cachePrefix(long stamp) {
            return "fs:" + stamp;
        }
    }

    private record FlowImageIcon(BufferedImage image) implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min(
                        (double) ICON_SIZE / Math.max(1, image.getWidth()),
                        (double) ICON_SIZE / Math.max(1, image.getHeight())
                );
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int offsetX = x + (ICON_SIZE - width) / 2;
                int offsetY = y + (ICON_SIZE - height) / 2;
                graphics2D.drawImage(image, offsetX, offsetY, width, height, null);
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
