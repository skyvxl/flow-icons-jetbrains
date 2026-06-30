package dev.flowicons.jetbrains;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.progress.ProgressIndicator;
import org.brotli.dec.BrotliInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FlowIconsUpdater {
    private static final String OPEN_VSX_API = "https://open-vsx.org/api/thang-nm/flow-icons";
    private static final String API_BASE = "https://legit-i9lq.onrender.com/flow-icons";
    private static final String USER_AGENT = "Flow Icons JetBrains/0.3.0";
    private static final long MAX_JSON_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_PACK_BYTES = 512L * 1024L * 1024L;
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static void extractDemoVsix(Path vsix, Path iconsDir, ProgressIndicator indicator) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(vsix))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                indicator.checkCanceled();
                if (entry.isDirectory()) {
                    continue;
                }

                String relativeName = FlowIconsIconPack.normalizeArchiveIconPath(entry.getName());
                if (relativeName == null || !FlowIconsIconPack.isSafeIconArchiveEntry(relativeName)) {
                    continue;
                }

                copySafe(zip, iconsDir, relativeName);
            }
        }
    }

    private static void extractPremiumTar(Path compressedTar, Path iconsDir, ProgressIndicator indicator) throws IOException {
        try (InputStream input = new BrotliInputStream(Files.newInputStream(compressedTar))) {
            byte[] header = new byte[512];
            while (readFully(input, header)) {
                indicator.checkCanceled();
                String name = readNullTerminated(header, 0, 100).trim();
                if (name.isEmpty()) {
                    break;
                }
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];

                String normalizedName = FlowIconsIconPack.normalizeArchiveIconPath(name);
                if (normalizedName == null) {
                    skipTarEntry(input, size);
                    continue;
                }

                if (type == '5' || normalizedName.endsWith("/")) {
                    Files.createDirectories(safeResolve(iconsDir, normalizedName));
                    skipTarEntry(input, size);
                } else if (size > 0 && (type == 0 || type == '0') && FlowIconsIconPack.isSafeIconArchiveEntry(normalizedName)) {
                    Path target = safeResolve(iconsDir, normalizedName);
                    Files.createDirectories(target.getParent());
                    copyTarEntry(input, target, size, indicator);
                    skipTarPadding(input, size);
                } else {
                    skipTarEntry(input, size);
                }
            }
        }
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

    private static void copyWithLimit(InputStream input, Path target, long maxBytes, BooleanSupplier canceled) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                copyWithLimit(input, output, maxBytes, canceled);
            }
            if (canceled.getAsBoolean()) {
                throw new IOException("Download canceled.");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(target);
            throw e;
        }
    }

    private static void copyWithLimit(InputStream input, OutputStream output, long maxBytes, BooleanSupplier canceled) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        while (true) {
            if (canceled.getAsBoolean()) {
                throw new IOException("Download canceled.");
            }

            int read = input.read(buffer);
            if (read < 0) {
                return;
            }

            total += read;
            if (total > maxBytes) {
                throw new IOException("Download too large.");
            }
            output.write(buffer, 0, read);
        }
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
        if (!FlowIconsIconPack.hasSupportedIconFile(iconsDir.resolve("deep"))) {
            throw new IOException("Downloaded Flow Icons pack does not contain supported icons.");
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

    private static boolean readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = input.read(target, offset, target.length - offset);
            if (read < 0) {
                if (offset == 0) {
                    return false;
                }
                throw new IOException("Unexpected end of tar archive.");
            }
            offset += read;
        }
        return true;
    }

    private static void copyTarEntry(InputStream input, Path target, long size, ProgressIndicator indicator) throws IOException {
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                indicator.checkCanceled();
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Unexpected end of tar archive.");
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static void skipTarEntry(InputStream input, long size) throws IOException {
        skipBytes(input, size + paddingFor(size));
    }

    private static void skipTarPadding(InputStream input, long size) throws IOException {
        skipBytes(input, paddingFor(size));
    }

    private static void skipBytes(InputStream input, long bytesToSkip) throws IOException {
        while (bytesToSkip > 0) {
            long skipped = input.skip(bytesToSkip);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new IOException("Unexpected end of tar archive.");
                }
                skipped = 1;
            }
            bytesToSkip -= skipped;
        }
    }

    private static long paddingFor(long size) {
        long remainder = size % 512L;
        return remainder == 0 ? 0 : 512L - remainder;
    }

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
                Path compressedTar = tempRoot.resolve("premium-icons.tar.br");
                downloadToFile(premiumInfo.url(), Map.of(), compressedTar, indicator);

                indicator.setText("Extracting premium icons");
                indicator.setFraction(0.55);
                extractPremiumTar(compressedTar, iconsDir, indicator);
            } else {
                installedVersion = extensionInfo.version();

                indicator.setText("Downloading demo Flow Icons package");
                indicator.setFraction(0.35);
                Path vsix = tempRoot.resolve("flow-icons.vsix");
                downloadToFile(extensionInfo.vsixUrl(), Map.of(), vsix, indicator);

                indicator.setText("Extracting demo icons");
                indicator.setFraction(0.55);
                extractDemoVsix(vsix, iconsDir, indicator);
            }

            indicator.setText("Building JetBrains icon mappings");
            indicator.setFraction(0.78);
            FlowIconsIconPack.buildMappings(iconsDir, tempPack.resolve("mappings"));
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
        JsonObject json = requestJson(OPEN_VSX_API, Map.of());
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
        byte[] body = downloadToBytes(url, headers, MAX_JSON_BYTES);
        JsonElement element = GSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonElement.class);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Unexpected JSON response from " + url);
        }
        return element.getAsJsonObject();
    }

    private byte[] downloadToBytes(String url, Map<String, String> headers, long maxBytes) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = send(url, headers);
        try (InputStream body = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copyWithLimit(body, output, maxBytes, () -> false);
            return output.toByteArray();
        }
    }

    private void downloadToFile(String url, Map<String, String> headers, Path target, ProgressIndicator indicator) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = send(url, headers);
        try (InputStream body = response.body()) {
            copyWithLimit(body, target, MAX_PACK_BYTES, indicator::isCanceled);
        }
    }

    private HttpResponse<InputStream> send(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMinutes(3))
                .setHeader("user-agent", USER_AGENT);
        headers.forEach(builder::setHeader);

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            try (InputStream body = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copyWithLimit(body, output, 64L * 1024L, () -> false);
                throw new IOException("Request failed with HTTP " + status + ": " + output.toString(StandardCharsets.UTF_8));
            }
        }
        return response;
    }

    public record UpdateResult(String message) {
    }

    private record ExtensionInfo(String version, String vsixUrl) {
    }

    private record PremiumInfo(String version, String url) {
    }
}
