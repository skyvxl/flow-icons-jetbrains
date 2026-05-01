package dev.flowicons.jetbrains;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFileManager;

public final class FlowIconsUiRefresher {
    private FlowIconsUiRefresher() {
    }

    public static void refreshOpenProjects() {
        ApplicationManager.getApplication().invokeLater(() -> {
            IconLoader.clearCache();
            FlowIconsFileIconProvider.clearAllCaches();
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);

            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    ProjectView.getInstance(project).refresh();
                }
            }
        });
    }
}
