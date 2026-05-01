package dev.flowicons.jetbrains;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class FlowIconsFileIconProvider implements FileIconProvider {
    private static final String DARK_THEME = "deep";
    private static final String LIGHT_THEME = "deep-light";
    private static final int ICON_SIZE = 18;

    private static final Map<String, ThemeMapping> MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
        FlowIconsSettings settings = FlowIconsSettings.getInstance();
        PackLocation packLocation = PackLocation.current(settings);
        String theme = resolveTheme(settings.getTheme());

        String mappingKey = packLocation.cachePrefix(settings.getIconPackStamp()) + ":" + theme;
        ThemeMapping mapping = MAPPINGS.computeIfAbsent(mappingKey, ignored -> loadMapping(packLocation, theme));
        String iconPath = mapping.iconPathFor(file);
        if (iconPath == null) {
            return null;
        }

        return loadCachedIcon(packLocation, settings, iconPath);
    }

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

    private static ThemeMapping loadMapping(PackLocation packLocation, String theme) {
        Properties properties = new Properties();
        try (InputStream stream = packLocation.openMapping(theme)) {
            if (stream == null) {
                return ThemeMapping.empty();
            }
            properties.load(stream);
        } catch (IOException ignored) {
            return ThemeMapping.empty();
        }
        return ThemeMapping.from(properties);
    }

    private interface PackLocation {
        @Nullable InputStream openMapping(String theme) throws IOException;

        @Nullable Icon loadIcon(String resourcePath);

        String cachePrefix(long stamp);

        static PackLocation current(FlowIconsSettings settings) {
            Path installedPack = settings.getInstalledPackDir();
            if (settings.hasInstalledPack()) {
                return new FileSystemPackLocation(installedPack);
            }
            return BuiltInPackLocation.INSTANCE;
        }
    }

    private enum BuiltInPackLocation implements PackLocation {
        INSTANCE;

        @Override
        public @Nullable InputStream openMapping(String theme) {
            return FlowIconsFileIconProvider.class.getResourceAsStream("/flow-icons/mappings/" + theme + ".properties");
        }

        @Override
        public @Nullable Icon loadIcon(String resourcePath) {
            try (InputStream stream = FlowIconsFileIconProvider.class.getResourceAsStream(resourcePath)) {
                return stream == null ? null : createIcon(stream);
            } catch (IOException ignored) {
                return null;
            }
        }

        @Override
        public String cachePrefix(long stamp) {
            return "builtin";
        }
    }

    private static final class FileSystemPackLocation implements PackLocation {
        private final Path root;

        private FileSystemPackLocation(Path root) {
            this.root = root;
        }

        @Override
        public @Nullable InputStream openMapping(String theme) throws IOException {
            Path mapping = root.resolve("mappings").resolve(theme + ".properties");
            return Files.isRegularFile(mapping) ? Files.newInputStream(mapping) : null;
        }

        @Override
        public @Nullable Icon loadIcon(String resourcePath) {
            String relativePath = resourcePath.replace('\\', '/').replaceFirst("^/flow-icons/", "");
            Path iconFile = root.resolve(relativePath.replace('/', java.io.File.separatorChar));
            try (InputStream stream = Files.isRegularFile(iconFile) ? Files.newInputStream(iconFile) : null) {
                return stream == null ? null : createIcon(stream);
            } catch (IOException ignored) {
                return null;
            }
        }

        @Override
        public String cachePrefix(long stamp) {
            return "fs:" + stamp;
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

    private static final class FlowImageIcon implements Icon {
        private final BufferedImage image;

        private FlowImageIcon(BufferedImage image) {
            this.image = image;
        }

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

    private static final class ThemeMapping {
        private final String defaultFile;
        private final String defaultDirectory;
        private final Map<String, String> fileStems;
        private final Map<String, String> fileSuffixes;
        private final Map<String, String> directoryNames;
        private final List<String> suffixesByLength;
        private final Map<String, String> filePathCache = new ConcurrentHashMap<>();
        private final Map<String, String> directoryPathCache = new ConcurrentHashMap<>();

        private ThemeMapping(
                String defaultFile,
                String defaultDirectory,
                Map<String, String> fileStems,
                Map<String, String> fileSuffixes,
                Map<String, String> directoryNames
        ) {
            this.defaultFile = defaultFile;
            this.defaultDirectory = defaultDirectory;
            this.fileStems = fileStems;
            this.fileSuffixes = fileSuffixes;
            this.directoryNames = directoryNames;

            List<String> suffixes = new ArrayList<>(fileSuffixes.keySet());
            suffixes.sort((left, right) -> Integer.compare(right.length(), left.length()));
            this.suffixesByLength = Collections.unmodifiableList(suffixes);
        }

        static ThemeMapping empty() {
            return new ThemeMapping(null, null, Map.of(), Map.of(), Map.of());
        }

        static ThemeMapping from(Properties properties) {
            Map<String, String> stems = new HashMap<>();
            Map<String, String> suffixes = new HashMap<>();
            Map<String, String> directories = new HashMap<>();

            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key.startsWith("file.stem.")) {
                    stems.put(key.substring("file.stem.".length()), value);
                } else if (key.startsWith("file.suffix.")) {
                    suffixes.put(key.substring("file.suffix.".length()), value);
                } else if (key.startsWith("dir.name.")) {
                    directories.put(key.substring("dir.name.".length()), value);
                }
            }

            return new ThemeMapping(
                    properties.getProperty("default.file"),
                    properties.getProperty("default.directory"),
                    Map.copyOf(stems),
                    Map.copyOf(suffixes),
                    Map.copyOf(directories)
            );
        }

        @Nullable String iconPathFor(VirtualFile file) {
            String lowerName = file.getName().toLowerCase(Locale.ROOT);
            if (file.isDirectory()) {
                return directoryPathCache.computeIfAbsent(lowerName, this::directoryIconPathFor);
            }
            return filePathCache.computeIfAbsent(lowerName, this::fileIconPathFor);
        }

        private String directoryIconPathFor(String lowerName) {
            return directoryNames.getOrDefault(lowerName, defaultDirectory);
        }

        private String fileIconPathFor(String lowerName) {
            String exact = fileStems.get(lowerName);
            if (exact != null) {
                return exact;
            }

            for (String suffix : suffixesByLength) {
                if (lowerName.equals(suffix) || lowerName.endsWith("." + suffix)) {
                    return fileSuffixes.get(suffix);
                }
            }

            return defaultFile;
        }
    }
}
