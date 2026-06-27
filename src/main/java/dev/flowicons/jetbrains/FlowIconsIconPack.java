package dev.flowicons.jetbrains;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

final class FlowIconsIconPack {
    private static final Gson GSON = new Gson();
    private static final List<ThemeSpec> THEMES = List.of(
            new ThemeSpec("deep", "deep"),
            new ThemeSpec("deep-light", "deep"),
            new ThemeSpec("dim", "dim"),
            new ThemeSpec("dim-light", "dim"),
            new ThemeSpec("dawn", "dawn"),
            new ThemeSpec("dawn-light", "dawn"),
            new ThemeSpec("you", "you"),
            new ThemeSpec("you-light", "you")
    );
    private static final Set<String> THEME_FOLDERS = new HashSet<>();
    private static final Set<String> THEME_JSONS = Set.of("deep.json", "dim.json", "dawn.json", "you.json", "flow-icons.json");
    private static final List<String> SUPPORTED_ICON_EXTENSIONS = List.of(".svg", ".png");
    private static final String MAPPING_OVERRIDES_FILE = "mapping-overrides.json";

    static {
        for (ThemeSpec theme : THEMES) {
            THEME_FOLDERS.add(theme.folder());
        }
    }

    private FlowIconsIconPack() {
    }

    static String normalizeArchiveIconPath(String archiveName) {
        String name = archiveName.replace('\\', '/');
        if (name.startsWith("extension/")) {
            name = name.substring("extension/".length());
        }

        int iconThemesMarker = name.indexOf("/icon_themes/");
        if (iconThemesMarker >= 0) {
            name = name.substring(iconThemesMarker + "/icon_themes/".length());
        }

        int iconsMarker = name.indexOf("/icons/");
        if (iconsMarker >= 0) {
            name = normalizeIconsRelativeName(name.substring(iconsMarker + "/icons/".length()));
        } else if (name.startsWith("icons/")) {
            name = normalizeIconsRelativeName(name.substring("icons/".length()));
        }

        for (String themeJson : THEME_JSONS) {
            if (name.endsWith("/" + themeJson)) {
                name = themeJson;
                break;
            }
        }

        if (name.endsWith("/" + MAPPING_OVERRIDES_FILE)) {
            name = MAPPING_OVERRIDES_FILE;
        }

        return isWantedIconPackEntry(name) ? name : null;
    }

    static boolean isWantedIconPackEntry(String relativeName) {
        if (MAPPING_OVERRIDES_FILE.equals(relativeName)) {
            return true;
        }
        if (THEME_JSONS.contains(relativeName)) {
            return true;
        }
        if (relativeName.startsWith("icons/")) {
            return true;
        }
        for (String folder : THEME_FOLDERS) {
            if (relativeName.equals(folder) || relativeName.startsWith(folder + "/")) {
                return true;
            }
        }
        return false;
    }

    static boolean isSafeIconArchiveEntry(String relativeName) {
        if (relativeName.contains("/._")
                || relativeName.startsWith("._")
                || relativeName.contains("PaxHeader")) {
            return false;
        }
        if (THEME_JSONS.contains(relativeName)) {
            return true;
        }
        if (MAPPING_OVERRIDES_FILE.equals(relativeName)) {
            return true;
        }
        for (String extension : SUPPORTED_ICON_EXTENSIONS) {
            if (relativeName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    static void buildMappings(Path iconsDir, Path mappingsDir) throws IOException {
        Files.createDirectories(mappingsDir);
        JsonObject overrides = loadMappingOverrides(iconsDir);
        for (ThemeSpec theme : THEMES) {
            buildMapping(iconsDir, mappingsDir, theme.folder(), theme.themeJsonName(), overrides);
        }

        if (!Files.isRegularFile(mappingsDir.resolve("deep.properties"))
                && Files.isRegularFile(iconsDir.resolve("flow-icons.json"))) {
            buildZedMappings(iconsDir, iconsDir.resolve("flow-icons.json"), mappingsDir, overrides);
        }
    }

    static boolean hasSupportedIconFile(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> Files.isRegularFile(path) && hasSupportedIconExtension(path.getFileName().toString()));
        }
    }

    static void applyBundledMappingOverrides(Properties properties, Function<String, String> iconPathById) throws IOException {
        applyOverrides(properties, iconPathById, loadBundledMappingOverrides());
    }

    private static String normalizeIconsRelativeName(String relativeName) {
        int slash = relativeName.indexOf('/');
        if (slash > 0) {
            String firstSegment = relativeName.substring(0, slash);
            if (THEME_FOLDERS.contains(firstSegment)) {
                return relativeName;
            }
        }
        if (THEME_JSONS.contains(relativeName)) {
            return relativeName;
        }
        return "icons/" + relativeName;
    }

    private static void buildMapping(Path iconsDir, Path mappingsDir, String folder, String themeJsonName, JsonObject overrides) throws IOException {
        Path folderDir = iconsDir.resolve(folder);
        Path themeJsonPath = iconsDir.resolve(themeJsonName + ".json");
        if (!Files.isDirectory(folderDir) || !Files.isRegularFile(themeJsonPath)) {
            return;
        }

        Map<String, String> fileIconPaths = new HashMap<>();
        try (var stream = Files.list(folderDir)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> hasSupportedIconExtension(path.getFileName().toString()))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String iconId = stripSupportedIconExtension(fileName);
                        if (iconId != null && !iconId.startsWith("folder_")) {
                            fileIconPaths.put(iconId, "/flow-icons/icons/" + folder + "/" + fileName);
                        }
                    });
        }

        JsonObject vscodeTheme = GSON.fromJson(Files.readString(themeJsonPath), JsonObject.class);
        Properties properties = new Properties();
        putPath(properties, "default.file", iconPath(folderDir, folder, "file"));
        putPath(properties, "default.directory", iconPath(folderDir, folder, "folder_gray"));

        for (Map.Entry<String, String> entry : readStringMap(objectValue(vscodeTheme, "fileNames")).entrySet()) {
            putIconPath(properties, "file.stem." + normalizeKey(entry.getKey()), fileIconPaths, entry.getValue());
        }

        for (Map.Entry<String, String> entry : readStringMap(objectValue(vscodeTheme, "fileExtensions")).entrySet()) {
            putIconPath(properties, "file.suffix." + normalizeKey(entry.getKey()), fileIconPaths, entry.getValue());
        }

        applyOverrides(properties, iconId -> iconPath(folderDir, folder, iconId), overrides);

        Map<String, String> folderNames = readStringMap(objectValue(vscodeTheme, "folderNames"));
        for (Map.Entry<String, String> entry : folderNames.entrySet()) {
            putPath(properties, "dir.name." + normalizeKey(entry.getKey()), iconPath(folderDir, folder, entry.getValue()));
        }

        writeProperties(mappingsDir.resolve(folder + ".properties"), properties);
    }

    private static void buildZedMappings(Path iconsDir, Path themeJsonPath, Path mappingsDir, JsonObject overrides) throws IOException {
        JsonObject zedTheme = GSON.fromJson(Files.readString(themeJsonPath), JsonObject.class);
        JsonElement themesElement = zedTheme.get("themes");
        if (themesElement == null || !themesElement.isJsonArray()) {
            return;
        }

        for (JsonElement themeElement : themesElement.getAsJsonArray()) {
            if (!themeElement.isJsonObject()) {
                continue;
            }

            JsonObject theme = themeElement.getAsJsonObject();
            String folder = zedThemeFolder(theme);
            if (folder == null || folder.isBlank()) {
                continue;
            }

            JsonObject fileIcons = objectValue(theme, "file_icons");
            Properties properties = new Properties();
            putPath(properties, "default.file", zedIconPath(pathFromIconEntry(objectValue(fileIcons, "default"))));

            JsonObject directoryIcons = objectValue(theme, "directory_icons");
            if (directoryIcons != null) {
                putPath(properties, "default.directory", zedIconPath(stringValue(directoryIcons.get("collapsed"))));
            }

            for (Map.Entry<String, String> entry : readStringMap(objectValue(theme, "file_stems")).entrySet()) {
                putPath(properties, "file.stem." + normalizeKey(entry.getKey()), zedIconPath(iconPathById(fileIcons, entry.getValue())));
            }

            for (Map.Entry<String, String> entry : readStringMap(objectValue(theme, "file_suffixes")).entrySet()) {
                putPath(properties, "file.suffix." + normalizeKey(entry.getKey()), zedIconPath(iconPathById(fileIcons, entry.getValue())));
            }

            applyOverrides(properties, iconId -> iconPath(iconsDir.resolve(folder), folder, iconId), overrides);

            JsonObject namedDirectories = objectValue(theme, "named_directory_icons");
            if (namedDirectories != null) {
                for (Map.Entry<String, JsonElement> entry : namedDirectories.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    putPath(
                            properties,
                            "dir.name." + normalizeKey(entry.getKey()),
                            zedIconPath(stringValue(entry.getValue().getAsJsonObject().get("collapsed")))
                    );
                }
            }

            if (!properties.isEmpty()) {
                writeProperties(mappingsDir.resolve(folder + ".properties"), properties);
            }
        }
    }

    private static JsonObject loadMappingOverrides(Path iconsDir) throws IOException {
        Path localOverrides = iconsDir.resolve(MAPPING_OVERRIDES_FILE);
        if (Files.isRegularFile(localOverrides)) {
            return GSON.fromJson(Files.readString(localOverrides), JsonObject.class);
        }
        return loadBundledMappingOverrides();
    }

    private static JsonObject loadBundledMappingOverrides() throws IOException {
        try (InputStream input = FlowIconsIconPack.class.getResourceAsStream("/flow-icons/" + MAPPING_OVERRIDES_FILE)) {
            if (input == null) {
                return null;
            }
            return GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    private static void applyOverrides(Properties properties, Function<String, String> iconPathById, JsonObject overrides) {
        if (overrides == null) {
            return;
        }

        for (Map.Entry<String, String> entry : readExactStringMap(objectValue(overrides, "fileNames")).entrySet()) {
            putPath(properties, "file.stem." + normalizeKey(entry.getKey()), iconPathById.apply(entry.getValue()));
        }

        for (Map.Entry<String, String> entry : readExactStringMap(objectValue(overrides, "nativeFileNames")).entrySet()) {
            putPath(properties, "file.native." + normalizeKey(entry.getKey()), normalizeKey(entry.getValue()));
        }

        for (Map.Entry<String, String> entry : readExactStringMap(objectValue(overrides, "fileGlobs")).entrySet()) {
            String tail = tailFromFileGlob(entry.getKey());
            if (tail != null) {
                putPath(properties, "file.tail." + tail, iconPathById.apply(entry.getValue()));
            }
        }
    }

    private static String iconPath(Path folderDir, String folder, String iconId) {
        for (String extension : SUPPORTED_ICON_EXTENSIONS) {
            Path icon = folderDir.resolve(iconId + extension);
            if (Files.isRegularFile(icon)) {
                return "/flow-icons/icons/" + folder + "/" + iconId + extension;
            }
        }
        return null;
    }

    private static boolean hasSupportedIconExtension(String fileName) {
        return stripSupportedIconExtension(fileName) != null;
    }

    private static String stripSupportedIconExtension(String fileName) {
        for (String extension : SUPPORTED_ICON_EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        return null;
    }

    private static String zedThemeFolder(JsonObject theme) {
        JsonObject directoryIcons = objectValue(theme, "directory_icons");
        if (directoryIcons != null) {
            String folder = folderFromIconPath(stringValue(directoryIcons.get("collapsed")));
            if (folder != null) {
                return folder;
            }
        }

        JsonObject fileIcons = objectValue(theme, "file_icons");
        return folderFromIconPath(pathFromIconEntry(objectValue(fileIcons, "default")));
    }

    private static String folderFromIconPath(String iconPath) {
        if (iconPath == null) {
            return null;
        }

        String normalized = iconPath.replace('\\', '/').replaceFirst("^\\./", "");
        int marker = normalized.indexOf("/icons/");
        if (marker >= 0) {
            normalized = normalized.substring(marker + "/icons/".length());
        } else if (normalized.startsWith("icons/")) {
            normalized = normalized.substring("icons/".length());
        }

        int slash = normalized.indexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : null;
    }

    private static String iconPathById(JsonObject fileIcons, String iconId) {
        if (fileIcons == null || iconId == null || iconId.isBlank()) {
            return null;
        }
        return pathFromIconEntry(objectValue(fileIcons, iconId));
    }

    private static String pathFromIconEntry(JsonObject iconEntry) {
        return iconEntry == null ? null : stringValue(iconEntry.get("path"));
    }

    private static String zedIconPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }

        String normalized = iconPath.replace('\\', '/').replaceFirst("^\\./", "");
        if (normalized.startsWith("icons/")) {
            normalized = normalized.substring("icons/".length());
        }
        return "/flow-icons/icons/" + normalized;
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String stringValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static Map<String, String> readStringMap(JsonObject jsonObject) {
        Map<String, String> result = new HashMap<>();
        if (jsonObject == null) {
            return result;
        }

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            addCaseVariations(result, entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static Map<String, String> readExactStringMap(JsonObject jsonObject) {
        Map<String, String> result = new HashMap<>();
        if (jsonObject == null) {
            return result;
        }

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private static void addCaseVariations(Map<String, String> result, String key, String value) {
        result.put(key, value);
        result.put(key.toLowerCase(Locale.ROOT), value);
        result.put(key.toUpperCase(Locale.ROOT), value);
        if (!key.isEmpty()) {
            result.put(key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1), value);
        }

        int dot = key.lastIndexOf('.');
        if (dot > 0 && dot < key.length() - 1) {
            String stem = key.substring(0, dot);
            String ext = key.substring(dot + 1);
            result.put(stem.toUpperCase(Locale.ROOT) + "." + ext.toLowerCase(Locale.ROOT), value);
        }
    }

    private static void putIconPath(Properties properties, String key, Map<String, String> fileIconPaths, String iconId) {
        putPath(properties, key, fileIconPaths.get(iconId));
    }

    private static void putPath(Properties properties, String key, String path) {
        if (path != null && !path.isBlank()) {
            properties.setProperty(key, path);
        }
    }

    private static void writeProperties(Path target, Properties properties) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
                writer.write(escapeProperty(key));
                writer.write('=');
                writer.write(escapeProperty(properties.getProperty(key)));
                writer.newLine();
            }
        }
    }

    private static String escapeProperty(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (current == ' ' || current == '#' || current == '!' || current == '=' || current == ':') {
                        result.append('\\');
                    }
                    result.append(current);
                }
            }
        }
        return result.toString();
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static String tailFromFileGlob(String glob) {
        String pattern = normalizeKey(glob.replace('\\', '/'));
        if (pattern.contains("/")) {
            return null;
        }
        while (pattern.startsWith("*")) {
            pattern = pattern.substring(1);
        }
        if (pattern.isBlank() || pattern.contains("*") || pattern.contains("?") || pattern.contains("[")) {
            return null;
        }
        return pattern;
    }

    private record ThemeSpec(String folder, String themeJsonName) {
    }
}
