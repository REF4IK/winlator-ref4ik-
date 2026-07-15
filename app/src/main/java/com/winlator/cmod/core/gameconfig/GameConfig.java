package com.winlator.cmod.core.gameconfig;

import org.json.JSONException;
import org.json.JSONObject;

public class GameConfig {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion;
    public String gameName;
    public String description;
    public String device;
    public String gpu;
    public long exportedAt;
    public JSONObject containerSettings;
    public JSONObject shortcutExtraData;

    public GameConfig() {
        schemaVersion = SCHEMA_VERSION;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("schemaVersion", schemaVersion);
            json.put("gameName", gameName);
            json.put("description", description != null ? description : "");
            json.put("device", device != null ? device : "");
            json.put("gpu", gpu != null ? gpu : "");
            json.put("exportedAt", exportedAt);
            json.put("containerSettings", containerSettings != null ? containerSettings : new JSONObject());
            json.put("shortcutExtraData", shortcutExtraData != null ? shortcutExtraData : new JSONObject());
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static GameConfig fromJson(JSONObject json) {
        GameConfig config = new GameConfig();
        config.schemaVersion = json.optInt("schemaVersion", SCHEMA_VERSION);
        config.gameName = json.optString("gameName", "");
        config.description = json.optString("description", "");
        config.device = json.optString("device", "");
        config.gpu = json.optString("gpu", "");
        config.exportedAt = json.optLong("exportedAt", System.currentTimeMillis());
        config.containerSettings = json.optJSONObject("containerSettings");
        config.shortcutExtraData = json.optJSONObject("shortcutExtraData");
        return config;
    }

    public static GameConfig fromJsonString(String jsonString) {
        try {
            return fromJson(new JSONObject(jsonString));
        } catch (JSONException e) {
            return null;
        }
    }
}
