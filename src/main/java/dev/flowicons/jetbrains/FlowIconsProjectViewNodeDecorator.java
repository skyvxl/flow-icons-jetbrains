package dev.flowicons.jetbrains;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;

import javax.swing.*;

public final class FlowIconsProjectViewNodeDecorator implements ProjectViewNodeDecorator, DumbAware {
    private final FlowIconsFileIconProvider provider = new FlowIconsFileIconProvider();

    @Override
    public void decorate(ProjectViewNode<?> node, PresentationData data) {
        VirtualFile file = node.getVirtualFile();
        if (file == null) {
            return;
        }

        Icon icon = provider.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, node.getProject());
        if (icon != null) {
            data.setIcon(icon);
        }
    }

    @Override
    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    }
}
