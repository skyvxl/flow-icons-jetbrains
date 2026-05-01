#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const sourceDir = path.join(root, "flow-icons-zed");
const targetDir = path.join(root, "src", "main", "resources", "flow-icons");
const themeJsonPath = path.join(sourceDir, "icon_themes", "flow-icons.json");

const themeJson = JSON.parse(fs.readFileSync(themeJsonPath, "utf8"));

fs.mkdirSync(path.join(targetDir, "mappings"), { recursive: true });
fs.cpSync(path.join(sourceDir, "icons"), path.join(targetDir, "icons"), { recursive: true });

for (const theme of themeJson.themes || []) {
  const folder = extractThemeFolder(theme);
  if (!folder) continue;

  const properties = new Map();
  putPath(properties, "default.file", theme.file_icons?.default?.path);
  putPath(properties, "default.directory", theme.directory_icons?.collapsed);

  for (const [name, iconId] of Object.entries(theme.file_stems || {})) {
    putIconPath(properties, `file.stem.${normalizeKey(name)}`, theme.file_icons, iconId);
  }

  for (const [suffix, iconId] of Object.entries(theme.file_suffixes || {})) {
    putIconPath(properties, `file.suffix.${normalizeKey(suffix)}`, theme.file_icons, iconId);
  }

  for (const [name, icons] of Object.entries(theme.named_directory_icons || {})) {
    putPath(properties, `dir.name.${normalizeKey(name)}`, icons?.collapsed);
  }

  const lines = [
    "# Generated from flow-icons-zed/icon_themes/flow-icons.json",
    ...[...properties.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, value]) => `${escapeProperty(key)}=${escapeProperty(value)}`),
    "",
  ];

  fs.writeFileSync(path.join(targetDir, "mappings", `${folder}.properties`), lines.join("\n"), "utf8");
}

function extractThemeFolder(theme) {
  const iconPath = theme?.directory_icons?.collapsed || theme?.file_icons?.default?.path;
  const match = iconPath && iconPath.match(/(?:^|\/)icons\/([^/]+)\//);
  return match ? match[1] : null;
}

function normalizeKey(value) {
  return value.toString().toLowerCase();
}

function putIconPath(properties, key, fileIcons, iconId) {
  const iconPath = fileIcons?.[iconId]?.path;
  putPath(properties, key, iconPath);
}

function putPath(properties, key, iconPath) {
  if (!iconPath) return;
  properties.set(key, "/flow-icons/" + iconPath.replace(/\\/g, "/").replace(/^\.?\//, ""));
}

function escapeProperty(value) {
  return value
    .toString()
    .replace(/\\/g, "\\\\")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\t/g, "\\t")
    .replace(/^([ #!=:])/, "\\$1")
    .replace(/([ #!=:])/g, "\\$1");
}

function rmWithRetry(target) {
  let lastError;
  for (let attempt = 0; attempt < 5; attempt++) {
    try {
      fs.rmSync(target, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 });
      return;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

function cleanFiles(directory) {
  if (!fs.existsSync(directory)) {
    fs.mkdirSync(directory, { recursive: true });
    return;
  }

  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      cleanFiles(fullPath);
    } else {
      rmWithRetry(fullPath);
    }
  }
}
