package dev.flowicons.jetbrains;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class FlowIconsConfigurable implements Configurable {
    private static final String[] THEMES = {
            FlowIconsSettings.THEME_AUTO,
            "deep",
            "deep-light",
            "dim",
            "dim-light",
            "dawn",
            "dawn-light"
    };

    private JPanel panel;
    private JPasswordField licenseField;
    private JComboBox<String> themeComboBox;
    private JLabel statusLabel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Flow Icons";
    }

    @Override
    public @Nullable JComponent createComponent() {
        FlowIconsSettings settings = FlowIconsSettings.getInstance();

        panel = new JPanel(new GridBagLayout());
        licenseField = new JPasswordField(36);
        themeComboBox = new JComboBox<>(THEMES);
        statusLabel = new JBLabel();
        JButton updateButton = new JButton("Update Icons");
        JButton resetButton = new JButton("Use Bundled Icons");

        updateButton.addActionListener(event -> {
            try {
                apply();
            } catch (ConfigurationException e) {
                Messages.showErrorDialog(panel, e.getMessage(), "Flow Icons");
                return;
            }

            JLabel currentStatusLabel = statusLabel;
            ProgressManager.getInstance().run(new Task.Backgroundable(null, "Updating Flow Icons", true) {
                @Override
                public void run(ProgressIndicator indicator) {
                    try {
                        FlowIconsUpdater.UpdateResult result = new FlowIconsUpdater().update(settings, indicator);
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            updateStatusLabel(currentStatusLabel, settings);
                            Messages.showInfoMessage(result.message(), "Flow Icons");
                        });
                    } catch (Exception e) {
                        javax.swing.SwingUtilities.invokeLater(() ->
                                Messages.showErrorDialog(e.getMessage(), "Flow Icons Update Failed"));
                    }
                }
            });
        });

        resetButton.addActionListener(event -> {
            try {
                FlowIconsUpdater.resetInstalledPack(settings);
                updateStatusLabel(statusLabel, settings);
                Messages.showInfoMessage("Bundled demo icons are active.", "Flow Icons");
            } catch (Exception e) {
                Messages.showErrorDialog(e.getMessage(), "Flow Icons Reset Failed");
            }
        });

        addRow(0, "License key", licenseField);
        addRow(1, "Palette", themeComboBox);
        addRow(2, "Status", statusLabel);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonsPanel.add(updateButton);
        buttonsPanel.add(resetButton);

        GridBagConstraints buttonConstraints = constraints(3, 1);
        buttonConstraints.anchor = GridBagConstraints.WEST;
        panel.add(buttonsPanel, buttonConstraints);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        FlowIconsSettings settings = FlowIconsSettings.getInstance();
        return !settings.getLicenseKey().equals(licenseText())
                || !settings.getTheme().equals(themeComboBox.getSelectedItem());
    }

    @Override
    public void apply() throws ConfigurationException {
        FlowIconsSettings settings = FlowIconsSettings.getInstance();
        settings.setLicenseKey(licenseText());
        settings.setTheme((String) themeComboBox.getSelectedItem());
        statusLabel.setText(statusText(settings));
    }

    @Override
    public void reset() {
        FlowIconsSettings settings = FlowIconsSettings.getInstance();
        licenseField.setText(settings.getLicenseKey());
        themeComboBox.setSelectedItem(settings.getTheme());
        statusLabel.setText(statusText(settings));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        licenseField = null;
        themeComboBox = null;
        statusLabel = null;
    }

    private void addRow(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = constraints(row, 0);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = constraints(row, 1);
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
    }

    private static GridBagConstraints constraints(int row, int column) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.weighty = 0.0;
        return constraints;
    }

    private String licenseText() {
        return new String(licenseField.getPassword()).trim();
    }

    private static void updateStatusLabel(@Nullable JLabel label, FlowIconsSettings settings) {
        if (label != null) {
            label.setText(statusText(settings));
        }
    }

    private static String statusText(FlowIconsSettings settings) {
        String source = settings.hasInstalledPack() ? "installed pack" : "bundled demo pack";
        String version = settings.getInstalledVersion().isBlank() ? "" : " (" + settings.getInstalledVersion() + ")";
        String status = settings.getLastUpdateStatus().isBlank() ? "" : " - " + settings.getLastUpdateStatus();
        return source + version + status;
    }
}
