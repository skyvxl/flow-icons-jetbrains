package dev.flowicons.jetbrains;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class FlowIconsThemeMapping {
    static final String NATIVE_FILE_PREFIX = "native-file:";

    private final String defaultFile;
    private final String defaultDirectory;
    private final Map<String, String> fileStems;
    private final Map<String, String> nativeFileAliases;
    private final Map<String, String> fileTails;
    private final Map<String, String> fileSuffixes;
    private final Map<String, String> directoryNames;
    private final Map<String, String> directoryPaths;
    private final List<String> tailsByLength;
    private final List<String> suffixesByLength;
    private final List<String> directoryPathsByLength;
    private final Map<String, String> filePathCache = new ConcurrentHashMap<>();
    private final Map<String, String> directoryPathCache = new ConcurrentHashMap<>();

    private FlowIconsThemeMapping(
            String defaultFile,
            String defaultDirectory,
            Map<String, String> fileStems,
            Map<String, String> nativeFileAliases,
            Map<String, String> fileTails,
            Map<String, String> fileSuffixes,
            Map<String, String> directoryNames,
            Map<String, String> directoryPaths
    ) {
        this.defaultFile = defaultFile;
        this.defaultDirectory = defaultDirectory;
        this.fileStems = fileStems;
        this.nativeFileAliases = nativeFileAliases;
        this.fileTails = fileTails;
        this.fileSuffixes = fileSuffixes;
        this.directoryNames = directoryNames;
        this.directoryPaths = directoryPaths;

        List<String> tails = new ArrayList<>(fileTails.keySet());
        tails.sort((left, right) -> Integer.compare(right.length(), left.length()));
        this.tailsByLength = Collections.unmodifiableList(tails);

        List<String> suffixes = new ArrayList<>(fileSuffixes.keySet());
        suffixes.sort((left, right) -> Integer.compare(right.length(), left.length()));
        this.suffixesByLength = Collections.unmodifiableList(suffixes);

        List<String> paths = new ArrayList<>(directoryPaths.keySet());
        paths.sort((left, right) -> Integer.compare(right.length(), left.length()));
        this.directoryPathsByLength = Collections.unmodifiableList(paths);
    }

    static FlowIconsThemeMapping empty() {
        return new FlowIconsThemeMapping(null, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    static FlowIconsThemeMapping from(Properties properties) {
        Map<String, String> stems = new HashMap<>();
        Map<String, String> nativeAliases = new HashMap<>();
        Map<String, String> tails = new HashMap<>();
        Map<String, String> suffixes = new HashMap<>();
        Map<String, String> directoryNames = new HashMap<>();
        Map<String, String> directoryPaths = new HashMap<>();

        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key.startsWith("file.stem.")) {
                stems.put(key.substring("file.stem.".length()), value);
            } else if (key.startsWith("file.native.")) {
                nativeAliases.put(key.substring("file.native.".length()), value);
            } else if (key.startsWith("file.tail.")) {
                tails.put(key.substring("file.tail.".length()), value);
            } else if (key.startsWith("file.suffix.")) {
                suffixes.put(key.substring("file.suffix.".length()), value);
            } else if (key.startsWith("dir.name.")) {
                String directoryKey = key.substring("dir.name.".length());
                if (directoryKey.contains("/") || directoryKey.contains("\\")) {
                    directoryPaths.put(normalizePath(directoryKey), value);
                } else {
                    directoryNames.put(directoryKey, value);
                }
            }
        }

        return new FlowIconsThemeMapping(
                properties.getProperty("default.file"),
                properties.getProperty("default.directory"),
                Map.copyOf(stems),
                Map.copyOf(nativeAliases),
                Map.copyOf(tails),
                Map.copyOf(suffixes),
                Map.copyOf(directoryNames),
                Map.copyOf(directoryPaths)
        );
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    String iconPathFor(String name, String path, boolean directory) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (directory) {
            String normalizedPath = normalizePath(path);
            return directoryPathCache.computeIfAbsent(normalizedPath, ignored -> directoryIconPathFor(lowerName, normalizedPath));
        }
        return filePathCache.computeIfAbsent(lowerName, this::fileIconPathFor);
    }

    private String directoryIconPathFor(String lowerName, String normalizedPath) {
        for (String directoryPath : directoryPathsByLength) {
            if (normalizedPath.equals(directoryPath) || normalizedPath.endsWith("/" + directoryPath)) {
                return directoryPaths.get(directoryPath);
            }
        }
        return directoryNames.getOrDefault(lowerName, defaultDirectory);
    }

    private String fileIconPathFor(String lowerName) {
        String exact = fileStems.get(lowerName);
        if (exact != null) {
            return exact;
        }

        String nativeAlias = nativeFileAliases.get(lowerName);
        if (nativeAlias != null) {
            return NATIVE_FILE_PREFIX + nativeAlias;
        }

        for (String tail : tailsByLength) {
            if (lowerName.endsWith(tail)) {
                return fileTails.get(tail);
            }
        }

        for (String suffix : suffixesByLength) {
            if (lowerName.equals(suffix) || lowerName.endsWith("." + suffix)) {
                return fileSuffixes.get(suffix);
            }
        }

        return defaultFile;
    }
}
