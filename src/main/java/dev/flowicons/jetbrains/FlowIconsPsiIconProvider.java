package dev.flowicons.jetbrains;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class FlowIconsPsiIconProvider extends IconProvider implements DumbAware {
    private final FlowIconsFileIconProvider delegate = new FlowIconsFileIconProvider();

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        return file == null ? null : delegate.getIcon(file, flags, element.getProject());
    }
}
