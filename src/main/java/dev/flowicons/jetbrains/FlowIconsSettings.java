package dev.flowicons.jetbrains;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@Service(Service.Level.APP)
@State(name = "FlowIconsSettings", storages = @Storage("flow-icons.xml"))
public final class FlowIconsSettings implements PersistentStateComponent<FlowIconsSettings.State> {
    public static final String THEME_AUTO = "auto";

    private State state = new State();

    public static FlowIconsSettings getInstance() {
        return ApplicationManager.getApplication().getService(FlowIconsSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getLicenseKey() {
        return nullToEmpty(state.licenseKey);
    }

    public void setLicenseKey(String licenseKey) {
        state.licenseKey = nullToEmpty(licenseKey).trim();
    }

    public String getTheme() {
        return nullToEmpty(state.theme).isBlank() ? THEME_AUTO : state.theme;
    }

    public void setTheme(String theme) {
        state.theme = nullToEmpty(theme).isBlank() ? THEME_AUTO : theme;
        touchIconPack();
    }

    public String getInstalledVersion() {
        return nullToEmpty(state.installedVersion);
    }

    public void setInstalledVersion(String installedVersion) {
        state.installedVersion = nullToEmpty(installedVersion);
    }

    public String getLastUpdateStatus() {
        return nullToEmpty(state.lastUpdateStatus);
    }

    public void setLastUpdateStatus(String lastUpdateStatus) {
        state.lastUpdateStatus = nullToEmpty(lastUpdateStatus);
    }

    public long getIconPackStamp() {
        return state.iconPackStamp;
    }

    public void touchIconPack() {
        state.iconPackStamp = System.currentTimeMillis();
        FlowIconsFileIconProvider.clearAllCaches();
        FlowIconsUiRefresher.refreshOpenProjects();
    }

    public Path getInstalledPackDir() {
        return Path.of(PathManager.getConfigPath(), "flow-icons", "installed-pack");
    }

    public Path getTempPackDir() {
        return Path.of(PathManager.getTempPath(), "flow-icons");
    }

    public Path getLegacyTempPackDir() {
        return Path.of(PathManager.getConfigPath(), "flow-icons", "download-tmp");
    }

    public boolean hasInstalledPack() {
        Path installedPackDir = getInstalledPackDir();
        return installedPackDir.resolve("mappings").resolve("deep.properties").toFile().isFile()
                && installedPackDir.resolve("icons").toFile().isDirectory();
    }

    public static final class State {
        public String licenseKey = "";
        public String theme = THEME_AUTO;
        public String installedVersion = "";
        public String lastUpdateStatus = "Using bundled demo icons.";
        public long iconPackStamp = System.currentTimeMillis();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
