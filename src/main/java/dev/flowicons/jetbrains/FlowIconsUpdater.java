package dev.flowicons.jetbrains;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.progress.ProgressIndicator;
import org.brotli.dec.BrotliInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FlowIconsUpdater {
    private static final String OPEN_VSX_API = "https://open-vsx.org/api/thang-nm/flow-icons";
    private static final String API_BASE = "https://legit-i9lq.onrender.com/flow-icons";
    private static final String USER_AGENT = "Flow Icons JetBrains/0.1.0";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateResult update(FlowIconsSettings settings, ProgressIndicator indicator) throws Exception {
        indicator.setIndeterminate(false);
        indicator.setText("Fetching Flow Icons extension info");
        indicator.setFraction(0.05);

        ExtensionInfo extensionInfo = getExtensionInfo();
        String licenseKey = settings.getLicenseKey();
        boolean premium = !licenseKey.isBlank();
        Path tempRoot = settings.getTempPackDir();
        long updateStamp = System.currentTimeMillis();
        Path tempPack = tempRoot.resolve("pack-" + updateStamp);
        Path installedPack = settings.getInstalledPackDir();
        Path installStaging = tempRoot.resolve("installing-pack-" + updateStamp);

        deleteDirectoryBestEffort(tempRoot);
        deleteDirectoryBestEffort(settings.getLegacyTempPackDir());
        Files.createDirectories(tempPack);

        try {
            Path iconsDir = tempPack.resolve("icons");
            Files.createDirectories(iconsDir);

            String installedVersion;
            if (premium) {
                indicator.setText("Checking premium Flow Icons package");
                indicator.setFraction(0.18);
                PremiumInfo premiumInfo = getPremiumInfo(licenseKey, extensionInfo.version());
                installedVersion = extensionInfo.version() + "-" + premiumInfo.version();

                indicator.setText("Downloading premium Flow Icons package");
                indicator.setFraction(0.35);
                byte[] compressedTar = download(premiumInfo.url(), Map.of());

                indicator.setText("Extracting premium icons");
                indicator.setFraction(0.55);
                extractPremiumTar(compressedTar, iconsDir);
            } else {
                installedVersion = extensionInfo.version();

                indicator.setText("Downloading demo Flow Icons package");
                indicator.setFraction(0.35);
                byte[] vsix = download(extensionInfo.vsixUrl(), Map.of());

                indicator.setText("Extracting demo icons");
                indicator.setFraction(0.55);
                extractDemoVsix(vsix, iconsDir);
            }

            indicator.setText("Building JetBrains icon mappings");
            indicator.setFraction(0.78);
            buildMappings(iconsDir, tempPack.resolve("mappings"));
            validatePack(tempPack);

            indicator.setText("Installing icon pack");
            indicator.setFraction(0.92);
            Files.createDirectories(installedPack.getParent());
            copyDirectory(tempPack, installStaging);
            validatePack(installStaging);
            deleteDirectoryWithRetry(installedPack);
            copyDirectory(installStaging, installedPack);
            validatePack(installedPack);
            deleteDirectoryBestEffort(installStaging);
            deleteDirectoryBestEffort(tempRoot);

            settings.setInstalledVersion(installedVersion);
            settings.setLastUpdateStatus(premium ? "Premium icons installed." : "Demo icons updated.");
            settings.touchIconPack();

            indicator.setFraction(1.0);
            return new UpdateResult(settings.getLastUpdateStatus());
        } catch (Exception e) {
            deleteDirectoryBestEffort(installStaging);
            deleteDirectoryBestEffort(tempRoot);
            throw e;
        }
    }

    private ExtensionInfo getExtensionInfo() throws IOException, InterruptedException {
        JsonObject json = requestJson(OPEN_VSX_API, Map.of("user-agent", USER_AGENT));
        String version = requiredString(json, "version");
        JsonObject files = json.getAsJsonObject("files");
        if (files == null) {
            throw new IOException("Open VSX response does not contain files metadata.");
        }
        String vsixUrl = requiredString(files, "download");
        return new ExtensionInfo(version, vsixUrl);
    }

    private PremiumInfo getPremiumInfo(String licenseKey, String extensionVersion) throws IOException, InterruptedException {
        JsonObject json = requestJson(API_BASE + "/version-3?v=" + extensionVersion, Map.of(
                "authorization", licenseKey,
                "machine-id", machineId(),
                "user-agent", USER_AGENT + "/" + extensionVersion
        ));
        return new PremiumInfo(requiredString(json, "version"), requiredString(json, "url"));
    }

    private JsonObject requestJson(String url, Map<String, String> headers) throws IOException, InterruptedException {
        byte[] body = download(url, headers);
        JsonElement element = GSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Unexpected JSON response from " + url);
        }
        return element.getAsJsonObject();
    }

    private byte[] download(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMinutes(3))
                .header("user-agent", USER_AGENT);
        headers.forEach(builder::header);

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Request failed with HTTP " + status + ": " + new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
    }

    private static void extractDemoVsix(byte[] vsix, Path iconsDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(vsix))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String relativeName = normalizeArchiveIconPath(entry.getName());
                if (relativeName == null) {
                    continue;
                }

                if (!isWantedIconPackEntry(relativeName)) {
                    continue;
                }

                copySafe(zip, iconsDir, relativeName);
            }
        }
    }

    private static boolean isWantedIconPackEntry(String relativeName) {
        if (relativeName.endsWith(".json")) {
            return relativeName.equals("deep.json") || relativeName.equals("dim.json") || relativeName.equals("dawn.json");
        }
        return relativeName.startsWith("deep/")
                || relativeName.startsWith("deep-light/")
                || relativeName.startsWith("dim/")
                || relativeName.startsWith("dim-light/")
                || relativeName.startsWith("dawn/")
                || relativeName.startsWith("dawn-light/");
    }

    private static boolean isSafeIconArchiveEntry(String relativeName) {
        return !relativeName.contains("/._")
                && !relativeName.startsWith("._")
                && !relativeName.contains("PaxHeader")
                && (relativeName.endsWith(".png") || relativeName.endsWith(".json"));
    }

    private static void extractPremiumTar(byte[] compressedTar, Path iconsDir) throws IOException {
        byte[] tarBytes;
        try (InputStream input = new BrotliInputStream(new ByteArrayInputStream(compressedTar))) {
            tarBytes = input.readAllBytes();
        }

        int offset = 0;
        while (offset + 512 <= tarBytes.length) {
            byte[] header = java.util.Arrays.copyOfRange(tarBytes, offset, offset + 512);
            String name = readNullTerminated(header, 0, 100).trim();
            if (name.isEmpty()) {
                break;
            }
            long size = parseOctal(header, 124, 12);
            char type = (char) header[156];
            offset += 512;

            String normalizedName = normalizeArchiveIconPath(name);
            if (normalizedName == null) {
                offset += Math.toIntExact(((size + 511) / 512) * 512);
                continue;
            }

            if (type == '5' || normalizedName.endsWith("/")) {
                Files.createDirectories(safeResolve(iconsDir, normalizedName));
            } else if (size > 0 && (type == 0 || type == '0') && isSafeIconArchiveEntry(normalizedName)) {
                Path target = safeResolve(iconsDir, normalizedName);
                Files.createDirectories(target.getParent());
                Files.write(target, java.util.Arrays.copyOfRange(tarBytes, offset, Math.toIntExact(offset + size)));
            }

            offset += Math.toIntExact(((size + 511) / 512) * 512);
        }
    }

    private static String normalizeArchiveIconPath(String archiveName) {
        String name = archiveName.replace('\\', '/');
        if (name.startsWith("extension/")) {
            name = name.substring("extension/".length());
        }
        if (name.startsWith("icons/")) {
            name = name.substring("icons/".length());
        }

        return isWantedIconPackEntry(name) ? name : null;
    }

    private static void buildMappings(Path iconsDir, Path mappingsDir) throws IOException {
        Files.createDirectories(mappingsDir);
        buildMapping(iconsDir, mappingsDir, "deep", "deep");
        buildMapping(iconsDir, mappingsDir, "deep-light", "deep");
        buildMapping(iconsDir, mappingsDir, "dim", "dim");
        buildMapping(iconsDir, mappingsDir, "dim-light", "dim");
        buildMapping(iconsDir, mappingsDir, "dawn", "dawn");
        buildMapping(iconsDir, mappingsDir, "dawn-light", "dawn");
    }

    private static void buildMapping(Path iconsDir, Path mappingsDir, String folder, String themeJsonName) throws IOException {
        Path folderDir = iconsDir.resolve(folder);
        Path themeJsonPath = iconsDir.resolve(themeJsonName + ".json");
        if (!Files.isDirectory(folderDir) || !Files.isRegularFile(themeJsonPath)) {
            return;
        }

        Map<String, String> fileIconPaths = new HashMap<>();
        try (var stream = Files.list(folderDir)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String iconId = fileName.substring(0, fileName.length() - ".png".length());
                        if (!iconId.startsWith("folder_")) {
                            fileIconPaths.put(iconId, "/flow-icons/icons/" + folder + "/" + fileName);
                        }
                    });
        }

        JsonObject vscodeTheme = GSON.fromJson(Files.readString(themeJsonPath), JsonObject.class);
        Properties properties = new Properties();
        putPath(properties, "default.file", "/flow-icons/icons/" + folder + "/file.png");
        putPath(properties, "default.directory", "/flow-icons/icons/" + folder + "/folder_gray.png");

        for (Map.Entry<String, String> entry : readStringMap(vscodeTheme.getAsJsonObject("fileNames")).entrySet()) {
            putIconPath(properties, "file.stem." + normalizeKey(entry.getKey()), fileIconPaths, entry.getValue());
        }

        for (Map.Entry<String, String> entry : readStringMap(vscodeTheme.getAsJsonObject("fileExtensions")).entrySet()) {
            putIconPath(properties, "file.suffix." + normalizeKey(entry.getKey()), fileIconPaths, entry.getValue());
        }

        Map<String, String> folderNames = readStringMap(vscodeTheme.getAsJsonObject("folderNames"));
        for (Map.Entry<String, String> entry : folderNames.entrySet()) {
            Path icon = folderDir.resolve(entry.getValue() + ".png");
            if (Files.isRegularFile(icon)) {
                putPath(properties, "dir.name." + normalizeKey(entry.getKey()), "/flow-icons/icons/" + folder + "/" + entry.getValue() + ".png");
            }
        }

        try (OutputStream output = Files.newOutputStream(mappingsDir.resolve(folder + ".properties"))) {
            properties.store(output, "Generated by Flow Icons JetBrains");
        }
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

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IOException("Response does not contain required field: " + key);
        }
        return value.getAsString();
    }

    private static void copySafe(InputStream input, Path root, String relativeName) throws IOException {
        Path target = safeResolve(root, relativeName);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file).toString());
                Files.createDirectories(targetFile.getParent());
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void validatePack(Path packDir) throws IOException {
        Path mappingsDir = packDir.resolve("mappings");
        Path iconsDir = packDir.resolve("icons");
        if (!Files.isRegularFile(mappingsDir.resolve("deep.properties"))) {
            throw new IOException("Downloaded Flow Icons pack does not contain mappings/deep.properties.");
        }
        if (!Files.isDirectory(iconsDir.resolve("deep"))) {
            throw new IOException("Downloaded Flow Icons pack does not contain icons/deep.");
        }

        try (var files = Files.list(iconsDir.resolve("deep"))) {
            boolean hasPng = files.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"));
            if (!hasPng) {
                throw new IOException("Downloaded Flow Icons pack does not contain PNG icons.");
            }
        }
    }

    public static void resetInstalledPack(FlowIconsSettings settings) throws IOException {
        deleteDirectoryWithRetry(settings.getInstalledPackDir());
        deleteDirectoryBestEffort(settings.getTempPackDir());
        deleteDirectoryBestEffort(settings.getLegacyTempPackDir());
        settings.setInstalledVersion("");
        settings.setLastUpdateStatus("Using bundled demo icons.");
        settings.touchIconPack();
    }

    private static Path safeResolve(Path root, String relativeName) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relativeName).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("Refusing to write outside icon pack directory: " + relativeName);
        }
        return target;
    }

    private static void deleteDirectoryWithRetry(Path directory) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                deleteDirectory(directory);
                return;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(150L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastError;
    }

    private static void deleteDirectoryBestEffort(Path directory) {
        try {
            deleteDirectoryWithRetry(directory);
        } catch (IOException ignored) {
            // Windows can keep recently touched files locked for a short time.
            // Leftover temp folders are safe to remove on the next update.
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String machineId() {
        try {
            String hostName = System.getenv("COMPUTERNAME");
            if (hostName == null || hostName.isBlank()) {
                hostName = InetAddress.getLocalHost().getHostName();
            }
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md5.digest(hostName.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | IOException e) {
            return "unknown";
        }
    }

    private static String readNullTerminated(byte[] bytes, int offset, int length) {
        int end = offset;
        int max = Math.min(bytes.length, offset + length);
        while (end < max && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] bytes, int offset, int length) {
        String value = readNullTerminated(bytes, offset, length).trim();
        return value.isBlank() ? 0 : Long.parseLong(value, 8);
    }

    public record UpdateResult(String message) {
    }

    private record ExtensionInfo(String version, String vsixUrl) {
    }

    private record PremiumInfo(String version, String url) {
    }
}
