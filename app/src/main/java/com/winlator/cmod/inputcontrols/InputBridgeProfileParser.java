package com.winlator.cmod.inputcontrols;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser that converts Input Bridge (.ibp) profiles into Winlator's native
 * profile format.
 *
 * Support features:
 *  1. Icon support: matches raw customIcon.iconName to imported Winlator icon packs.
 *  2. Position alignment: converts IB's top-left coordinates to Winlator's center-based coords.
 *  3. Styles and Colors: imports ARGB fill, border, and text colors.
 */
public class InputBridgeProfileParser {

    private static final int DEFAULT_REF_W = 1920;
    private static final int DEFAULT_REF_H = 1080;
    private static final double BASE_HALF = 0.05;

    // Maps Windows Virtual Key codes (VK_*) to Winlator Binding names
    private static final Map<Integer, String> vkToBinding = new HashMap<>();

    static {
        // Numbers
        vkToBinding.put(0x30, "KEY_0");
        vkToBinding.put(0x31, "KEY_1");
        vkToBinding.put(0x32, "KEY_2");
        vkToBinding.put(0x33, "KEY_3");
        vkToBinding.put(0x34, "KEY_4");
        vkToBinding.put(0x35, "KEY_5");
        vkToBinding.put(0x36, "KEY_6");
        vkToBinding.put(0x37, "KEY_7");
        vkToBinding.put(0x38, "KEY_8");
        vkToBinding.put(0x39, "KEY_9");

        // Alphabetic
        for (int vk = 0x41; vk <= 0x5A; vk++) {
            char c = (char) vk;
            vkToBinding.put(vk, "KEY_" + c);
        }

        // Special Keys
        vkToBinding.put(0x1B, "KEY_ESC");
        vkToBinding.put(0x0D, "KEY_ENTER");
        vkToBinding.put(0x09, "KEY_TAB");
        vkToBinding.put(0x20, "KEY_SPACE");
        vkToBinding.put(0x08, "KEY_BKSP");
        vkToBinding.put(0x2E, "KEY_DEL");
        vkToBinding.put(0x2D, "KEY_INSERT");

        vkToBinding.put(0x10, "KEY_SHIFT_L"); // VK_SHIFT
        vkToBinding.put(0xA0, "KEY_SHIFT_L"); // VK_LSHIFT
        vkToBinding.put(0xA1, "KEY_SHIFT_R"); // VK_RSHIFT

        vkToBinding.put(0x11, "KEY_CTRL_L");  // VK_CONTROL
        vkToBinding.put(0xA2, "KEY_CTRL_L");  // VK_LCONTROL
        vkToBinding.put(0xA3, "KEY_CTRL_R");  // VK_RCONTROL

        vkToBinding.put(0x12, "KEY_ALT_L");   // VK_MENU
        vkToBinding.put(0xA4, "KEY_ALT_L");   // VK_LMENU
        vkToBinding.put(0xA5, "KEY_ALT_R");   // VK_RMENU

        vkToBinding.put(0x26, "KEY_UP");
        vkToBinding.put(0x27, "KEY_RIGHT");
        vkToBinding.put(0x28, "KEY_DOWN");
        vkToBinding.put(0x25, "KEY_LEFT");

        vkToBinding.put(0x24, "KEY_HOME");
        vkToBinding.put(0x23, "KEY_END");
        vkToBinding.put(0x21, "KEY_PG_UP");
        vkToBinding.put(0x22, "KEY_PG_DOWN");

        vkToBinding.put(0x14, "KEY_CAPS_LOCK");
        vkToBinding.put(0x90, "KEY_NUM_LOCK");
        vkToBinding.put(0x2C, "KEY_PRTSCN");

        // Function keys
        for (int i = 1; i <= 12; i++) {
            vkToBinding.put(0x70 + i - 1, "KEY_F" + i);
        }

        // Numpad keys
        vkToBinding.put(0x60, "KEY_KP_0");
        vkToBinding.put(0x61, "KEY_KP_1");
        vkToBinding.put(0x62, "KEY_KP_2");
        vkToBinding.put(0x63, "KEY_KP_3");
        vkToBinding.put(0x64, "KEY_KP_4");
        vkToBinding.put(0x65, "KEY_KP_5");
        vkToBinding.put(0x66, "KEY_KP_6");
        vkToBinding.put(0x67, "KEY_KP_7");
        vkToBinding.put(0x68, "KEY_KP_8");
        vkToBinding.put(0x69, "KEY_KP_9");

        vkToBinding.put(0x6B, "KEY_KP_ADD");
        vkToBinding.put(0x6D, "KEY_MINUS"); // Map keypad subtract to minus

        // OEM / Punctuation
        vkToBinding.put(0xBA, "KEY_SEMICOLON");
        vkToBinding.put(0xBC, "KEY_COMMA");
        vkToBinding.put(0xBE, "KEY_PERIOD");
        vkToBinding.put(0xBF, "KEY_SLASH");
        vkToBinding.put(0xC0, "KEY_GRAVE");
        vkToBinding.put(0xDB, "KEY_BRACKET_LEFT");
        vkToBinding.put(0xDC, "KEY_BACKSLASH");
        vkToBinding.put(0xDD, "KEY_BRACKET_RIGHT");
        vkToBinding.put(0xDE, "KEY_APOSTROPHE");
    }

    // Friendly labels for common buttons when no pack is loaded
    private static final Map<String, String> iconNameToLabel = new HashMap<>();

    static {
        iconNameToLabel.put("btn_a",     "A");
        iconNameToLabel.put("btn_b",     "B");
        iconNameToLabel.put("btn_x",     "X");
        iconNameToLabel.put("btn_y",     "Y");
        iconNameToLabel.put("lt",        "LT");
        iconNameToLabel.put("rt",        "RT");
        iconNameToLabel.put("lb",        "LB");
        iconNameToLabel.put("rb",        "RB");
        iconNameToLabel.put("l2",        "L2");
        iconNameToLabel.put("r2",        "R2");
        iconNameToLabel.put("l1",        "L1");
        iconNameToLabel.put("r1",        "R1");
        iconNameToLabel.put("l3",        "L3");
        iconNameToLabel.put("r3",        "R3");
        iconNameToLabel.put("start",     "⏵");
        iconNameToLabel.put("select",    "☰");
        iconNameToLabel.put("menu",      "☰");
        iconNameToLabel.put("back",      "↩");
        iconNameToLabel.put("home",      "⌂");
        iconNameToLabel.put("up",        "▲");
        iconNameToLabel.put("down",      "▼");
        iconNameToLabel.put("left",      "◀");
        iconNameToLabel.put("right",     "▶");
        iconNameToLabel.put("ok",        "OK");
        iconNameToLabel.put("esc",       "ESC");
        iconNameToLabel.put("enter",     "↵");
        iconNameToLabel.put("shift",     "⇧");
        iconNameToLabel.put("ctrl",      "CTRL");
        iconNameToLabel.put("alt",       "ALT");
        iconNameToLabel.put("space",     "SPC");
        iconNameToLabel.put("tab",       "TAB");
    }

    public static boolean isInputBridgeProfile(JSONObject data) {
        return data.has("touchControlElements")
                || data.has("controllersDatas")
                || data.has("ibDriverRate");
    }

    public static JSONObject parseProfile(JSONObject ibData, Context context) throws JSONException {
        JSONObject winData = new JSONObject();

        // Name
        String name = ibData.optString("name", "Imported Input Bridge");
        winData.put("name", name);
        winData.put("cursorSpeed", 1.0f);

        // Reference resolution
        int refWidth  = DEFAULT_REF_W;
        int refHeight = DEFAULT_REF_H;
        if (ibData.has("refResolution")) {
            JSONObject res = ibData.getJSONObject("refResolution");
            int rx = res.optInt("x", -1);
            int ry = res.optInt("y", -1);
            if (rx > 0 && ry > 0) { refWidth = rx; refHeight = ry; }
        }

        JSONArray winElements = new JSONArray();
        if (ibData.has("touchControlElements")) {
            JSONArray ibElements = ibData.getJSONArray("touchControlElements");
            for (int i = 0; i < ibElements.length(); i++) {
                JSONObject ibElem = ibElements.getJSONObject(i);
                JSONObject winElem = convertElement(ibElem, refWidth, refHeight, context);
                if (winElem != null) winElements.put(winElem);
            }
        }

        winData.put("elements", winElements);
        return winData;
    }

    private static JSONObject convertElement(JSONObject ibElem, int refWidth, int refHeight, Context context)
            throws JSONException {

        JSONObject winElem = new JSONObject();

        // ── Type ──────────────────────────────────────────────────────────────
        int ibType = ibElem.optInt("type", 1);
        String winType;
        switch (ibType) {
            case 1:  winType = "BUTTON";         break;
            case 2:  winType = "COMBO_BUTTON";   break;
            case 3:  winType = "D_PAD";          break;
            case 4:  winType = "STICK";          break;
            case 5:  winType = "TRACKPAD";       break;
            case 9:  winType = "STEERING_WHEEL"; break;
            default: winType = "BUTTON";         break;
        }
        winElem.put("type", winType);

        // ── Shape ─────────────────────────────────────────────────────────────
        int ibShape = ibElem.optInt("buttonShape", 0);
        String winShape;
        switch (ibShape) {
            case 0:  winShape = "CIRCLE";      break;
            case 1:  winShape = "ROUND_RECT";  break;
            case 2:  winShape = "RECT";        break;
            case 3:  winShape = "CIRCLE";      break;
            default: winShape = "CIRCLE";      break;
        }
        winElem.put("shape", winShape);

        // ── Scale ─────────────────────────────────────────────────────────────
        double ibScale = ibElem.optInt("scale", 100);
        double winScale = ibScale / 100.0;
        winElem.put("scale", winScale);

        // ── Position (top-left to center correction + normalisation + clamp) ──
        // Default size in IB is 150px. We offset by half of it scaled to get center coordinate.
        double halfW = 75.0 * winScale;
        double halfH = 75.0 * winScale;

        double normX = 0.5, normY = 0.5;
        if (ibElem.has("position")) {
            JSONObject pos = ibElem.getJSONObject("position");
            double rawX = pos.optInt("x", refWidth / 2);
            double rawY = pos.optInt("y", refHeight / 2);
            normX = (rawX + halfW) / refWidth;
            normY = (rawY + halfH) / refHeight;
        }
        // Clamp inside screen bounds
        double halfSize = BASE_HALF * winScale;
        normX = Math.max(halfSize, Math.min(1.0 - halfSize, normX));
        normY = Math.max(halfSize, Math.min(1.0 - halfSize, normY));
        winElem.put("x", normX);
        winElem.put("y", normY);

        // ── Toggle / border ───────────────────────────────────────────────────
        winElem.put("toggleSwitch", ibElem.optBoolean("useTriggerMode", false));
        winElem.put("hideBorder",   false);

        // ── Colors Mapping ───────────────────────────────────────────────────
        int winBorderColor = 0;
        int winFillColor = 0;
        int winTextColor = 0;

        if (ibElem.has("shapeFineTuneData")) {
            JSONObject shapeData = ibElem.getJSONObject("shapeFineTuneData");
            if (shapeData.has("strokeColor")) {
                winBorderColor = shapeData.getJSONObject("strokeColor").optInt("color", 0);
            }
            if (shapeData.has("fillColor")) {
                winFillColor = shapeData.getJSONObject("fillColor").optInt("color", 0);
            }
        }

        if (ibElem.has("textFineTuneData")) {
            JSONObject textData = ibElem.getJSONObject("textFineTuneData");
            if (textData.has("color")) {
                winTextColor = textData.getJSONObject("color").optInt("color", 0);
            }
        }

        winElem.put("borderColor", winBorderColor);
        winElem.put("fillColor",   winFillColor);
        winElem.put("textColor",   winTextColor);

        // ── Label text & icon ─────────────────────────────────────────────────
        String label = ibElem.optString("customText", "").trim();
        int winIconId = 0;

        if (ibElem.has("customIcon")) {
            JSONObject iconObj = ibElem.getJSONObject("customIcon");
            String rawName = iconObj.optString("iconName", "").trim();
            if (!rawName.isEmpty()) {
                winIconId = findIconIdInPacks(context, rawName);
                if (winIconId == 0 && label.isEmpty()) {
                    // Fall back to mapping the icon name to text label
                    String mapped = iconNameToLabel.get(rawName.toLowerCase());
                    label = (mapped != null) ? mapped : rawName;
                }
            }
        }

        winElem.put("text",      label);
        winElem.put("iconId",    winIconId);
        winElem.put("iconScale", 1.0);

        // ── Opacity ───────────────────────────────────────────────────────────
        winElem.put("opacity",     ibElem.optDouble("alpha", 0.5));
        winElem.put("fillOpacity", 0.12);
        winElem.put("rotation",    0.0);

        // ── Bindings ──────────────────────────────────────────────────────────
        JSONArray bindings = new JSONArray();

        switch (winType) {
            case "BUTTON": {
                int buttonType  = ibElem.optInt("buttonType", 0);
                String bindName = "NONE";
                if      (buttonType == 0) bindName = mapVkToBindingName(ibElem.optInt("windowsKeyCode", -1));
                else if (buttonType == 1) bindName = mapMouseToBindingName(ibElem.optInt("mouseCode", 0));
                else if (buttonType == 2) bindName = mapXInputToBindingName(ibElem.optInt("xinputCode", 0x1000));
                bindings.put(bindName);
                bindings.put("NONE");
                bindings.put("NONE");
                bindings.put("NONE");
                break;
            }
            case "COMBO_BUTTON": {
                int mainVk = ibElem.optInt("windowsKeyCode", -1);
                if (mainVk != -1) {
                    String b = mapVkToBindingName(mainVk);
                    if (!b.equals("NONE")) bindings.put(b);
                }
                if (ibElem.has("combinationCodes")) {
                    JSONArray comboCodes = ibElem.getJSONArray("combinationCodes");
                    for (int j = 0; j < comboCodes.length(); j++) {
                        JSONObject comboObj = comboCodes.getJSONObject(j);
                        int vk = comboObj.optInt("code", -1);
                        if (vk != -1) {
                            String b = mapVkToBindingName(vk);
                            if (!b.equals("NONE")) bindings.put(b);
                        }
                    }
                }
                while (bindings.length() < 4) bindings.put("NONE");
                break;
            }
            case "D_PAD":
            case "STICK": {
                boolean isXI   = ibElem.optBoolean("enableXI", false);
                int stickSide  = ibElem.optInt("stickSide", 0);
                if (isXI && stickSide == 0) {
                    bindings.put("GAMEPAD_LEFT_THUMB_UP");
                    bindings.put("GAMEPAD_LEFT_THUMB_RIGHT");
                    bindings.put("GAMEPAD_LEFT_THUMB_DOWN");
                    bindings.put("GAMEPAD_LEFT_THUMB_LEFT");
                } else if (isXI) {
                    bindings.put("GAMEPAD_RIGHT_THUMB_UP");
                    bindings.put("GAMEPAD_RIGHT_THUMB_RIGHT");
                    bindings.put("GAMEPAD_RIGHT_THUMB_DOWN");
                    bindings.put("GAMEPAD_RIGHT_THUMB_LEFT");
                } else {
                    bindings.put(mapVkToBindingName(ibElem.optInt("codeUp",    0x57))); // W
                    bindings.put(mapVkToBindingName(ibElem.optInt("codeRight", 0x44))); // D
                    bindings.put(mapVkToBindingName(ibElem.optInt("codeDown",  0x53))); // S
                    bindings.put(mapVkToBindingName(ibElem.optInt("codeLeft",  0x41))); // A
                }
                break;
            }
            case "TRACKPAD": {
                bindings.put("MOUSE_MOVE_UP");
                bindings.put("MOUSE_MOVE_RIGHT");
                bindings.put("MOUSE_MOVE_DOWN");
                bindings.put("MOUSE_MOVE_LEFT");
                break;
            }
            case "STEERING_WHEEL": {
                bindings.put("NONE");
                bindings.put(mapVkToBindingName(ibElem.optInt("codeRight", 0x44)));
                bindings.put("NONE");
                bindings.put(mapVkToBindingName(ibElem.optInt("codeLeft",  0x41)));
                break;
            }
            default: {
                bindings.put("NONE");
                bindings.put("NONE");
                bindings.put("NONE");
                bindings.put("NONE");
            }
        }

        winElem.put("bindings", bindings);
        return winElem;
    }

    private static int findIconIdInPacks(Context context, String iconName) {
        if (context == null || iconName == null || iconName.trim().isEmpty()) return 0;
        iconName = iconName.trim().toLowerCase();

        try {
            IconPackManager packManager = new IconPackManager(context);
            List<IconPackManager.IconPack> packs = packManager.getIconPacks();

            for (IconPackManager.IconPack pack : packs) {
                List<File> files = pack.getIconFiles();
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    String fname = f.getName().toLowerCase();
                    if (fname.endsWith(".png")) {
                        fname = fname.substring(0, fname.length() - 4);
                    }
                    if (fname.equals(iconName)) {
                        return IconPackManager.getPackIconId(pack.id, i);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static String mapVkToBindingName(int vk) {
        if (vk == -1) return "NONE";
        String name = vkToBinding.get(vk);
        return name != null ? name : "NONE";
    }

    private static String mapMouseToBindingName(int mc) {
        switch (mc) {
            case 0: return "MOUSE_LEFT_BUTTON";
            case 1: return "MOUSE_RIGHT_BUTTON";
            case 2: return "MOUSE_MIDDLE_BUTTON";
            default: return "MOUSE_LEFT_BUTTON";
        }
    }

    private static String mapXInputToBindingName(int xi) {
        switch (xi) {
            case 0x1000: return "GAMEPAD_BUTTON_A";
            case 0x2000: return "GAMEPAD_BUTTON_B";
            case 0x4000: return "GAMEPAD_BUTTON_X";
            case 0x8000: return "GAMEPAD_BUTTON_Y";
            case 0x100:  return "GAMEPAD_BUTTON_L1";
            case 0x200:  return "GAMEPAD_BUTTON_R1";
            case 0x40:   return "GAMEPAD_BUTTON_L3";
            case 0x80:   return "GAMEPAD_BUTTON_R3";
            case 0x41:   return "GAMEPAD_BUTTON_L2";
            case 0x42:   return "GAMEPAD_BUTTON_R2";
            case 0x10:   return "GAMEPAD_BUTTON_START";
            case 0x20:   return "GAMEPAD_BUTTON_SELECT";
            case 1:      return "GAMEPAD_DPAD_UP";
            case 2:      return "GAMEPAD_DPAD_DOWN";
            case 4:      return "GAMEPAD_DPAD_LEFT";
            case 8:      return "GAMEPAD_DPAD_RIGHT";
            default:     return "NONE";
        }
    }
}
