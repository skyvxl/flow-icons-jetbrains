#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const sourceDir = path.resolve(process.argv[2] || path.join(root, "flow-icons-zed"));
const targetDir = path.join(root, "src", "main", "resources", "flow-icons");
const themeJsonPath = path.join(sourceDir, "icon_themes", "flow-icons.json");
const overridesPath = path.join(targetDir, "mapping-overrides.json");

const themeJson = JSON.parse(fs.readFileSync(themeJsonPath, "utf8"));
const mappingOverrides = readJsonIfExists(overridesPath) || {};

rmWithRetry(targetDir);
fs.mkdirSync(path.join(targetDir, "mappings"), { recursive: true });
fs.cpSync(path.join(sourceDir, "icons"), path.join(targetDir, "icons"), { recursive: true });
fs.writeFileSync(overridesPath, `${JSON.stringify(mappingOverrides, null, 2)}\n`, "utf8");

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

  applyOverrides(properties, folder);

  for (const [name, icons] of Object.entries(theme.named_directory_icons || {})) {
    putPath(properties, `dir.name.${normalizeKey(name)}`, icons?.collapsed);
  }

  const lines = [
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

function applyOverrides(properties, folder) {
  for (const [fileName, iconId] of Object.entries(mappingOverrides.fileNames || {})) {
    putPath(properties, `file.stem.${normalizeKey(fileName)}`, iconPathForId(folder, iconId));
  }

  for (const [fileName, targetFileName] of Object.entries(mappingOverrides.nativeFileNames || {})) {
    properties.set(`file.native.${normalizeKey(fileName)}`, normalizeKey(targetFileName));
  }

  for (const [glob, iconId] of Object.entries(mappingOverrides.fileGlobs || {})) {
    const tail = tailFromFileGlob(glob);
    if (tail) {
      putPath(properties, `file.tail.${tail}`, iconPathForId(folder, iconId));
    }
  }
}

function iconPathForId(folder, iconId) {
  for (const extension of [".svg", ".png"]) {
    const iconPath = path.join(targetDir, "icons", folder, `${iconId}${extension}`);
    if (fs.existsSync(iconPath)) {
      return `icons/${folder}/${iconId}${extension}`;
    }
  }
  return null;
}

function tailFromFileGlob(glob) {
  let pattern = normalizeKey(glob).replace(/\\/g, "/");
  if (pattern.includes("/")) return null;
  while (pattern.startsWith("*")) {
    pattern = pattern.slice(1);
  }
  if (!pattern || pattern.includes("*") || pattern.includes("?") || pattern.includes("[")) {
    return null;
  }
  return pattern;
}

function readJsonIfExists(filePath) {
  return fs.existsSync(filePath) ? JSON.parse(fs.readFileSync(filePath, "utf8")) : null;
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
