package com.winlator.cmod.core;

import com.winlator.cmod.math.Mathf;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class WineRegistryEditor implements Closeable {
    private final File file;
    private final File cloneFile;
    private boolean modified = false;
    private boolean createKeyIfNotExist = true;
    private int lastParentKeyPosition = 0;
    private String lastParentKey = "";

    public static class Location {
        public final int offset;
        public final int start;
        public final int end;

        public Location(int offset, int start, int end) {
            this.offset = offset;
            this.start = start;
            this.end = end;
        }

        public int length() {
            return end - start;
        }
    }

    public static class RegValue {
        public final String name;
        public final String type;
        public final String value;

        public RegValue(String name, String type, String value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    public WineRegistryEditor(File file) {
        this.file = file;
        cloneFile = FileUtils.createTempFile(file.getParentFile(), FileUtils.getBasename(file.getPath()));
        if (!file.isFile()) {
            try {
                cloneFile.createNewFile();
            } catch (IOException e) {
            }
        } else FileUtils.copy(file, cloneFile);
    }

    private static String escape(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String str) {
        return str.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean lineHasName(String line) {
        int index;
        return (index = line.indexOf('"')) != -1 &&
                (index = line.indexOf('"', index)) != -1 &&
                (index = line.indexOf('=', index)) != -1;
    }

    @Override
    public void close() {
        if (modified && cloneFile.exists()) {
            cloneFile.renameTo(file);
        } else cloneFile.delete();
    }

    private void resetLastParentKeyPositionIfNeed(String newKey) {
        int lastIndex = newKey.lastIndexOf("\\");
        if (lastIndex == -1) {
            lastParentKeyPosition = 0;
            lastParentKey = "";
            return;
        }

        String parentKey = newKey.substring(0, lastIndex);
        if (!parentKey.equals(lastParentKey)) lastParentKeyPosition = 0;
        lastParentKey = parentKey;
    }

    public void setCreateKeyIfNotExist(boolean createKeyIfNotExist) {
        this.createKeyIfNotExist = createKeyIfNotExist;
    }

    private Location createKey(String key) {
        lastParentKeyPosition = 0;
        Location location = getParentKeyLocation(key);
        boolean success = false;
        int offset = 0;
        int totalLength = 0;

        char[] buffer = new char[StreamUtils.BUFFER_SIZE];
        File tempFile = FileUtils.createTempFile(file.getParentFile(), FileUtils.getBasename(file.getPath()));

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE);
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile), StreamUtils.BUFFER_SIZE)) {

            int length;
            for (int i = 0, end = location != null ? location.end + 1 : (int) cloneFile.length(); i < end; i += length) {
                length = Math.min(buffer.length, end - i);
                reader.read(buffer, 0, length);
                writer.write(buffer, 0, length);
                totalLength += length;
            }

            offset = totalLength;
            long ticks1601To1970 = 86400L * (369 * 365 + 89) * 10000000;
            long currentTime = System.currentTimeMillis() + ticks1601To1970;
            String content = "\n[" + escape(key) + "] " + ((currentTime - ticks1601To1970) / 1000) +
                    String.format(Locale.ENGLISH, "\n#time=%x%08x", currentTime >> 32, (int) currentTime) + "\n";
            writer.write(content);
            totalLength += content.length() - 1;

            while ((length = reader.read(buffer)) != -1) writer.write(buffer, 0, length);
            success = true;
        } catch (IOException e) {
        }

        if (success) {
            modified = true;
            tempFile.renameTo(cloneFile);
            return new Location(offset, totalLength, totalLength);
        } else {
            tempFile.delete();
            return null;
        }
    }

    public String getStringValue(String key, String name) {
        return getStringValue(key, name, null);
    }

    public String getStringValue(String key, String name, String fallback) {
        String value = getRawValue(key, name);
        return value != null ? value.substring(1, value.length() - 1) : fallback;
    }

    public void setStringValue(String key, String name, String value) {
        setRawValue(key, name, value != null ? "\"" + escape(value) + "\"" : "\"\"");
    }

    public Integer getDwordValue(String key, String name) {
        return getDwordValue(key, name, null);
    }

    public Integer getDwordValue(String key, String name, Integer fallback) {
        String value = getRawValue(key, name);
        return value != null ? Integer.decode("0x" + value.substring(6)) : fallback;
    }

    public void setDwordValue(String key, String name, int value) {
        setRawValue(key, name, "dword:" + String.format("%08x", value));
    }

    public void setQwordValue(String key, String name, long value) {
        setRawValue(key, name, String.format(Locale.ENGLISH, "hex(b):%02x,%02x,%02x,%02x,%02x,%02x,%02x,%02x",
                value & 0xff, (value >> 8) & 0xff, (value >> 16) & 0xff, (value >> 24) & 0xff,
                (value >> 32) & 0xff, (value >> 40) & 0xff, (value >> 48) & 0xff, (value >> 56) & 0xff));
    }

    public boolean keyExists(String key) {
        lastParentKeyPosition = 0;
        return getKeyLocation(key) != null;
    }

    public String getHexValue(String key, String name) {
        return getHexValue(key, name, null);
    }

    public String getHexValue(String key, String name, String fallback) {
        String value = getRawValue(key, name);
        if (value == null) return fallback;
        int start = value.indexOf(":");
        if (start == -1) return fallback;
        return value.substring(start + 1).replace("\\", "").replace("\n", "").replace(" ", "");
    }

    public List<String> getSubKeys(String key) {
        ArrayList<String> subKeys = new ArrayList<>();
        String escapedKey = key.isEmpty() ? null : escape(key) + "\\\\";

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("[")) continue;
                int endIndex = line.indexOf(']');
                if (endIndex <= 1) continue;
                String fullKey = line.substring(1, endIndex);
                String rest;
                if (escapedKey == null) rest = fullKey;
                else if (fullKey.startsWith(escapedKey)) rest = fullKey.substring(escapedKey.length());
                else continue;
                if (rest.isEmpty()) continue;
                int slash = rest.indexOf("\\\\");
                String child = slash == -1 ? rest : rest.substring(0, slash);
                if (child.isEmpty()) continue;
                String unescapedChild = unescape(child);
                if (!subKeys.contains(unescapedChild)) subKeys.add(unescapedChild);
            }
        } catch (IOException e) {
        }
        return subKeys;
    }

    public List<RegValue> getValues(String key) {
        ArrayList<RegValue> values = new ArrayList<>();
        lastParentKeyPosition = 0;
        Location keyLocation = getKeyLocation(key);
        if (keyLocation == null) return values;

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            reader.skip(keyLocation.start);
            String line;
            int totalLength = 0;
            String currentName = null;
            String currentRaw = null;
            StringBuilder currentValue = null;

            while ((line = reader.readLine()) != null && totalLength < keyLocation.length()) {
                if (line.startsWith("[")) break;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    if (currentName != null && currentValue != null) {
                        values.add(buildRegValue(currentName, currentRaw, currentValue.toString()));
                        currentName = null;
                        currentRaw = null;
                        currentValue = null;
                    }
                    totalLength += line.length() + 1;
                    continue;
                }
                if (trimmed.startsWith("\"") || trimmed.startsWith("@")) {
                    if (currentName != null && currentValue != null) {
                        values.add(buildRegValue(currentName, currentRaw, currentValue.toString()));
                    }
                    int eqIndex = trimmed.indexOf('=');
                    if (eqIndex == -1) continue;
                    String namePart = trimmed.substring(0, eqIndex).trim();
                    currentName = null;
                    if (namePart.startsWith("\"")) {
                        currentName = unescape(namePart.substring(1, Math.max(namePart.length() - 1, 1)));
                        if (currentName.isEmpty()) currentName = null;
                    }
                    currentRaw = trimmed.substring(eqIndex + 1).trim();
                    currentValue = new StringBuilder();
                } else if (currentValue != null) {
                    if (currentValue.length() > 0 && currentRaw != null && !currentRaw.endsWith("\\") &&
                            currentValue.charAt(currentValue.length() - 1) != ',' && !trimmed.startsWith(",")) {
                        currentValue.append(",");
                    }
                    currentValue.append(trimmed);
                }
                totalLength += line.length() + 1;
            }
            if (currentName != null && currentValue != null) {
                values.add(buildRegValue(currentName, currentRaw, currentValue.toString()));
            }
        } catch (IOException e) {
        }
        return values;
    }

    private static RegValue buildRegValue(String name, String raw, String value) {
        String type;
        String result;
        if (raw != null && raw.startsWith("dword:")) {
            type = "Dword";
            result = raw.substring(6);
        } else if (raw != null && raw.startsWith("hex(b):")) {
            type = "Qword";
            result = raw.substring(7).replace("\\", "").replace(" ", "");
        } else if (raw != null && raw.startsWith("hex(2):")) {
            type = "ExpandString";
            result = raw.substring(7);
        } else if (raw != null && raw.startsWith("hex(7):")) {
            type = "MultiString";
            result = raw.substring(7);
        } else if (raw != null && raw.startsWith("hex:")) {
            type = "Hex";
            result = raw.substring(4).replace("\\", "").replace(" ", "");
        } else if (raw != null && raw.startsWith("\"")) {
            type = "String";
            int end = raw.lastIndexOf('"');
            result = end > 1 ? unescape(raw.substring(1, end)) : value;
        } else {
            type = "Other";
            result = value;
        }
        return new RegValue(name, type, result);
    }

    public void setHexValue(String key, String name, String value) {
        int start = (int) Mathf.roundTo(name.length(), 2) + 7;
        StringBuilder lines = new StringBuilder();
        for (int i = 0, j = start; i < value.length(); i++) {
            if (i > 0 && (i % 2) == 0) lines.append(",");
            if (j++ > 56) {
                lines.append("\\\n  ");
                j = 8;
            }
            lines.append(value.charAt(i));
        }
        setRawValue(key, name, "hex:" + lines);
    }

    public void setHexValue(String key, String name, byte[] bytes) {
        StringBuilder data = new StringBuilder();
        for (byte b : bytes)
            data.append(String.format(Locale.ENGLISH, "%02x", Byte.toUnsignedInt(b)));
        setHexValue(key, name, data.toString());
    }

    private String getRawValue(String key, String name) {
        lastParentKeyPosition = 0;
        Location keyLocation = getKeyLocation(key);
        if (keyLocation == null) return null;

        Location valueLocation = getValueLocation(keyLocation, name);
        if (valueLocation == null) return null;
        boolean success = false;
        char[] buffer = new char[valueLocation.length()];

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            reader.skip(valueLocation.start);
            success = reader.read(buffer) == buffer.length;
        } catch (IOException e) {
        }
        return success ? unescape(new String(buffer)) : null;
    }

    private void setRawValue(String key, String name, String value) {
        resetLastParentKeyPositionIfNeed(key);

        Location keyLocation = getKeyLocation(key);
        if (keyLocation == null) {
            if (createKeyIfNotExist) {
                keyLocation = createKey(key);
            } else return;
        }

        Location valueLocation = getValueLocation(keyLocation, name);
        char[] buffer = new char[StreamUtils.BUFFER_SIZE];
        boolean success = false;

        File tempFile = FileUtils.createTempFile(file.getParentFile(), FileUtils.getBasename(file.getPath()));

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE);
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile), StreamUtils.BUFFER_SIZE)) {

            int length;
            for (int i = 0, end = valueLocation != null ? valueLocation.start : keyLocation.end; i < end; i += length) {
                length = Math.min(buffer.length, end - i);
                reader.read(buffer, 0, length);
                writer.write(buffer, 0, length);
            }

            if (valueLocation == null) {
                writer.write("\n" + (name != null ? "\"" + escape(name) + "\"" : "@") + "=" + value);
            } else {
                writer.write(value);
                reader.skip(valueLocation.length());
            }

            while ((length = reader.read(buffer)) != -1) writer.write(buffer, 0, length);
            success = true;
        } catch (IOException e) {
        }

        if (success) {
            modified = true;
            tempFile.renameTo(cloneFile);
        } else tempFile.delete();
    }

    public void removeValue(String key, String name) {
        lastParentKeyPosition = 0;
        Location keyLocation = getKeyLocation(key);
        if (keyLocation == null) return;

        Location valueLocation = getValueLocation(keyLocation, name);
        if (valueLocation == null) return;
        removeRegion(valueLocation);
    }

    public boolean removeKey(String key) {
        return removeKey(key, false);
    }

    public List<String> getAllSubKeys(String key) {
        ArrayList<String> result = new ArrayList<>();
        collectSubKeys(key, result);
        return result;
    }

    public List<String> searchKeys(String query, int limit) {
        ArrayList<String> result = new ArrayList<>();
        String q = query.toLowerCase(Locale.ENGLISH);
        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("[")) continue;
                int endIndex = line.indexOf(']');
                if (endIndex <= 1) continue;
                String fullKey = line.substring(1, endIndex);
                String unescaped = unescape(fullKey);
                if (unescaped.toLowerCase(Locale.ENGLISH).contains(q)) {
                    result.add(unescaped);
                    if (result.size() >= limit) break;
                }
            }
        } catch (IOException e) {
        }
        return result;
    }

    private void collectSubKeys(String key, List<String> result) {
        result.add(key);
        for (String subKey : getSubKeys(key)) {
            collectSubKeys(key.isEmpty() ? subKey : key + "\\" + subKey, result);
        }
    }

    public String exportReg(String key, String hive) {
        StringBuilder sb = new StringBuilder();
        sb.append("Windows Registry Editor Version 5.00\n\n");
        for (String currentKey : getAllSubKeys(key)) {
            String exportPath = hive + (currentKey.isEmpty() ? "" : "\\" + currentKey);
            sb.append("[").append(exportPath).append("]\n");
            for (RegValue value : getValues(currentKey)) {
                String namePart = value.name != null ? "\"" + escape(value.name) + "\"" : "@";
                switch (value.type) {
                    case "Dword":
                        sb.append(namePart).append("=dword:").append(value.value).append("\n");
                        break;
                    case "Qword":
                        sb.append(namePart).append("=hex(b):").append(value.value).append("\n");
                        break;
                    case "ExpandString":
                        sb.append(namePart).append("=hex(2):").append(value.value).append("\n");
                        break;
                    case "MultiString":
                        sb.append(namePart).append("=hex(7):").append(value.value).append("\n");
                        break;
                    case "Hex":
                        sb.append(namePart).append("=hex:").append(value.value).append("\n");
                        break;
                    default:
                        sb.append(namePart).append("=\"").append(escape(value.value)).append("\"\n");
                        break;
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean removeKey(String key, boolean removeTree) {
        lastParentKeyPosition = 0;
        boolean removed = false;
        if (removeTree) {
            Location location;
            while ((location = getKeyLocation(key, true)) != null) {
                if (removeRegion(location)) removed = true;
            }
        } else {
            Location location = getKeyLocation(key, false);
            if (location != null && removeRegion(location)) removed = true;
        }
        return removed;
    }

    private boolean removeRegion(Location location) {
        char[] buffer = new char[StreamUtils.BUFFER_SIZE];
        boolean success = false;

        File tempFile = FileUtils.createTempFile(file.getParentFile(), FileUtils.getBasename(file.getPath()));

        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE);
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile), StreamUtils.BUFFER_SIZE)) {

            int length = 0;
            for (int i = 0; i < location.offset; i += length) {
                length = Math.min(buffer.length, location.offset - i);
                reader.read(buffer, 0, length);
                writer.write(buffer, 0, length);
            }

            boolean skipLine = length > 1 && buffer[length - 1] == '\n';
            reader.skip(location.end - location.offset + (skipLine ? 1 : 0));
            while ((length = reader.read(buffer)) != -1) writer.write(buffer, 0, length);
            success = true;
        } catch (IOException e) {
        }

        if (success) {
            modified = true;
            tempFile.renameTo(cloneFile);
        } else tempFile.delete();
        return success;
    }

    private Location getKeyLocation(String key) {
        return getKeyLocation(key, false);
    }

    private Location getKeyLocation(String key, boolean keyAsPrefix) {
        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            int lastIndex = key.lastIndexOf("\\");
            String parentKey = lastParentKeyPosition == 0 && lastIndex != -1 ? "[" + escape(key.substring(0, lastIndex)) : null;

            if (lastParentKeyPosition > 0) reader.skip(lastParentKeyPosition);
            key = "[" + escape(key) + (!keyAsPrefix ? "]" : "");
            int totalLength = lastParentKeyPosition;
            int start = -1;
            int end = -1;
            int emptyLines = 0;
            int offset = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (start == -1) {
                    if (parentKey != null && line.startsWith(parentKey)) {
                        lastParentKeyPosition = totalLength;
                        parentKey = null;
                    }

                    if (parentKey == null && line.startsWith(key)) {
                        offset = totalLength - 1;
                        start = totalLength + line.length() + 1;
                    }
                } else {
                    if (line.startsWith("[")) {
                        end = Math.max(-1, totalLength - emptyLines - 1);
                        break;
                    } else emptyLines = line.isEmpty() ? emptyLines + 1 : 0;
                }
                totalLength += line.length() + 1;
            }

            if (end == -1) end = totalLength - 1;
            return start != -1 ? new Location(offset, start, end) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private Location getParentKeyLocation(String key) {
        String[] parts = key.split("\\\\");
        ArrayList<String> stack = new ArrayList<>(Arrays.asList(parts).subList(0, parts.length - 1));

        while (!stack.isEmpty()) {
            String currentKey = String.join("\\", stack);
            Location location = getKeyLocation(currentKey, true);
            if (location != null) return location;
            stack.remove(stack.size() - 1);
        }

        return null;
    }

    private Location getValueLocation(Location keyLocation, String name) {
        if (keyLocation.start == keyLocation.end) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(cloneFile), StreamUtils.BUFFER_SIZE)) {
            reader.skip(keyLocation.start);
            name = name != null ? "\"" + escape(name) + "\"=" : "@=";
            int totalLength = 0;
            int start = -1;
            int end = -1;
            int offset = 0;

            String line;
            while ((line = reader.readLine()) != null && totalLength < keyLocation.length()) {
                if (start == -1) {
                    if (line.startsWith(name)) {
                        offset = totalLength - 1;
                        start = totalLength + name.length();
                    }
                } else {
                    if (line.isEmpty() || lineHasName(line)) {
                        end = totalLength - 1;
                        break;
                    }
                }
                totalLength += line.length() + 1;
            }

            if (end == -1) end = totalLength - 1;
            return start != -1 ? new Location(keyLocation.start + offset, keyLocation.start + start, keyLocation.start + end) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void importReg(String regFile) {
        try {
            JSONObject jobj = new JSONObject(regFile);
            Iterator<String> iterator = jobj.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                JSONArray entries = jobj.getJSONArray(key);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    String type = entry.getString("type");
                    String name = (entry.getString("name").isEmpty()) ? null : entry.getString("name");
                    String value = entry.getString("value");
                    switch (type) {
                        case "String":
                            setStringValue(key, name, value);
                            break;
                        case "Dword":
                            setDwordValue(key, name, Integer.parseInt(value));
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Импорт текстового .reg файла (формат "Windows Registry Editor Version 5.00" / "WINE REGISTRY Version 2").
     * Поддерживаются: строковые значения, dword, hex, hex(b) (QWORD), hex(2), hex(7).
     * Префиксы HKEY_* отрезаются — ключи применяются к текущему файлу как есть.
     */
    public void importRegFile(String regText) {
        if (regText == null || regText.isEmpty()) return;
        String[] lines = regText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String currentKey = null;
        String currentName = null;
        String currentRaw = null;
        StringBuilder currentValue = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith(";") || line.startsWith("#") || line.startsWith("WINE REGISTRY") ||
                    line.startsWith("Windows Registry Editor")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                if (currentKey != null && currentRaw != null) {
                    applyImportedValue(currentKey, currentName, currentRaw, currentValue.toString());
                }
                currentKey = stripHivePrefix(line.substring(1, line.length() - 1));
                ensureKey(currentKey);
                currentName = null;
                currentRaw = null;
                currentValue = null;
                continue;
            }
            if (line.startsWith("\"") || line.startsWith("@")) {
                if (currentKey != null && currentRaw != null) {
                    applyImportedValue(currentKey, currentName, currentRaw, currentValue.toString());
                }
                int eqIndex = line.indexOf('=');
                if (eqIndex == -1) continue;
                String namePart = line.substring(0, eqIndex).trim();
                currentName = null;
                if (namePart.startsWith("\"")) {
                    currentName = unescape(namePart.substring(1, Math.max(namePart.length() - 1, 1)));
                    if (currentName.isEmpty()) currentName = null;
                }
                currentRaw = line.substring(eqIndex + 1).trim();
                int colonIndex = currentRaw.indexOf(':');
                currentValue = new StringBuilder(colonIndex != -1 ? currentRaw.substring(colonIndex + 1) : "");
            } else if (currentValue != null) {
                if (currentValue.length() > 0 && currentRaw != null && !currentRaw.endsWith("\\") &&
                        currentValue.charAt(currentValue.length() - 1) != ',' && !line.startsWith(",")) {
                    currentValue.append(",");
                }
                currentValue.append(line);
            }
        }
        if (currentKey != null && currentRaw != null) {
            applyImportedValue(currentKey, currentName, currentRaw, currentValue.toString());
        }
    }

    private void ensureKey(String key) {
        if (key == null || key.isEmpty() || keyExists(key)) return;
        String[] parts = key.split("\\\\");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (current.length() > 0) current.append("\\");
            current.append(part);
            if (!keyExists(current.toString())) createKey(current.toString());
        }
    }

    private void applyImportedValue(String key, String name, String raw, String value) {
        if (raw.startsWith("\"")) {
            setStringValue(key, name, unescape(raw.substring(1, Math.max(raw.lastIndexOf('"'), 1))));
        } else if (raw.startsWith("dword:")) {
            try {
                setDwordValue(key, name, (int) Long.parseLong(raw.substring(6), 16));
            } catch (NumberFormatException e) {
            }
        } else if (raw.startsWith("hex")) {
            String data = value.replace("\\", "").replace(" ", "");
            int colonIndex = raw.indexOf(':');
            String prefix = colonIndex != -1 ? raw.substring(0, colonIndex + 1) : "hex:";
            setRawValue(key, name, prefix + data);
        } else {
            setStringValue(key, name, raw);
        }
    }

    private static String stripHivePrefix(String key) {
        if (key.equals("HKEY_LOCAL_MACHINE") || key.equals("HKEY_CURRENT_USER")) return "";
        String[] prefixes = {"HKEY_LOCAL_MACHINE\\", "HKEY_CURRENT_USER\\", "HKEY_USERS\\.DEFAULT\\", "HKEY_USERS\\"};
        for (String prefix : prefixes) {
            if (key.startsWith(prefix)) return key.substring(prefix.length());
        }
        return key;
    }
}
