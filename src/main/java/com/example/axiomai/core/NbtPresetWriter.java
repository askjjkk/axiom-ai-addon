package com.example.axiomai.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Saves a Script Brush preset in the same file format Axiom uses:
 * a gzip-compressed NBT file with Version, Settings{Script} and Name.
 */
public final class NbtPresetWriter {
    public static final String AXIOM_VERSION = "Axiom/5.4.2";

    private NbtPresetWriter() {
    }

    /** Folder where Axiom keeps script brush presets, given Minecraft's "config" folder. */
    public static Path axiomFolder(Path minecraftConfigDir) {
        return minecraftConfigDir.resolve("axiom").resolve("tool_presets").resolve("script_brush");
    }

    /** The saved file and the name the preset really got. */
    public record Saved(Path file, String presetName) {
    }

    /** Writes a new preset. Never overwrites: adds " 2", " 3"... to the name if needed. */
    public static Saved write(Path folder, String presetName, String script) throws IOException {
        Files.createDirectories(folder);
        String name = presetName;
        Path target = folder.resolve(fileBase(name) + ".nbt");
        for (int n = 2; Files.exists(target); n++) {
            name = presetName + " " + n;
            target = folder.resolve(fileBase(name) + ".nbt");
        }
        Files.write(target, build(name, script));
        return new Saved(target, name);
    }

    /** Replaces the content of an existing preset file (same file, same name unless a new name is given). */
    public static Saved overwrite(Path file, String presetName, String script) throws IOException {
        Files.write(file, build(presetName, script));
        return new Saved(file, presetName);
    }

    /**
     * Gives a preset a new name. The script is read from the old file (so manual edits made in Axiom are kept),
     * the preset is saved under a fresh file name and the old file is removed.
     */
    public static Saved rename(Path folder, Path oldFile, String newName) throws IOException {
        String script = readScript(oldFile);
        String name = newName.strip();
        if (name.isEmpty()) throw new IOException("The name is empty.");
        Path target = folder.resolve(fileBase(name) + ".nbt");
        String finalName = name;
        for (int n = 2; Files.exists(target) && !target.equals(oldFile); n++) {
            finalName = name + " " + n;
            target = folder.resolve(fileBase(finalName) + ".nbt");
        }
        Files.write(target, build(finalName, script));
        if (!target.equals(oldFile)) Files.deleteIfExists(oldFile);
        return new Saved(target, finalName);
    }

    /** Reads Settings.Script out of a preset file written by Axiom or by this addon. */
    public static String readScript(Path file) throws IOException {
        String script = readPreset(file)[0];
        if (script == null) throw new IOException("This preset has no script.");
        return script;
    }

    /** The Name stored inside a preset file, or an empty text if it has none. */
    public static String readName(Path file) throws IOException {
        String name = readPreset(file)[1];
        return name == null ? "" : name;
    }

    /** {script, name} of a preset file (null where something is missing). */
    private static String[] readPreset(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readByte() != 10) throw new IOException("Not a preset file.");
            in.readUTF();
            String[] found = new String[2];
            readCompound(in, 0, found);
            return found;
        }
    }

    /** Walks one compound and remembers "Script" (anywhere) and "Name" (top level only). */
    private static void readCompound(DataInputStream in, int depth, String[] found) throws IOException {
        while (true) {
            int type = in.readByte();
            if (type == 0) return;
            String key = in.readUTF();
            if (type == 8) {
                String value = in.readUTF();
                if (key.equals("Script")) found[0] = value;
                if (key.equals("Name") && depth == 0) found[1] = value;
            } else if (type == 10) {
                readCompound(in, depth + 1, found);
            } else {
                skipValue(in, type);
            }
        }
    }

    private static void skipValue(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipBytes(1);
            case 2 -> in.skipBytes(2);
            case 3, 5 -> in.skipBytes(4);
            case 4, 6 -> in.skipBytes(8);
            case 7 -> in.skipBytes(in.readInt());
            case 8 -> in.readUTF();
            case 9 -> {
                int itemType = in.readByte();
                int count = in.readInt();
                for (int i = 0; i < count; i++) skipValue(in, itemType);
            }
            case 10 -> {
                while (true) {
                    int t = in.readByte();
                    if (t == 0) break;
                    in.readUTF();
                    skipValue(in, t);
                }
            }
            case 11 -> in.skipBytes(in.readInt() * 4);
            case 12 -> in.skipBytes(in.readInt() * 8);
            default -> throw new IOException("Unknown tag " + type);
        }
    }

    static byte[] build(String name, String script) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);          // root compound
            out.writeUTF("");           // root has an empty name
            writeString(out, "Version", AXIOM_VERSION);
            out.writeByte(10);          // compound "Settings"
            out.writeUTF("Settings");
            writeString(out, "Script", script);
            out.writeByte(0);           // end of Settings
            writeString(out, "Name", name);
            out.writeByte(0);           // end of root
        } catch (UTFDataFormatException e) {
            throw new IOException("The script is too long to be saved as a preset (limit is about 65000 bytes).", e);
        }
        return bytes.toByteArray();
    }

    private static void writeString(DataOutputStream out, String key, String value) throws IOException {
        out.writeByte(8);
        out.writeUTF(key);
        out.writeUTF(value);
    }

    // a b v g d e zh z i y k l m n o p r s t u f h c ch sh sch (hard) y (soft) e yu ya
    private static final String[] CYRILLIC = {"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n",
            "o", "p", "r", "s", "t", "u", "f", "h", "c", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};

    /** "AI Moss Patches" -> "ai_moss_patches": lower case, Cyrillic letters become Latin, other symbols become "_". */
    static String fileBase(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
            else if (c >= '\u0430' && c <= '\u044f') sb.append(CYRILLIC[c - '\u0430']);
            else if (c == '\u0451') sb.append("yo");
            else sb.append('_');
        }
        String base = sb.toString();
        if (base.replace("_", "").isEmpty()) base = "ai_brush";
        return base;
    }
}
