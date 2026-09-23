package com.rgds.dashboard;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

final class UpdateAssets {
    interface Loader { String load(String url) throws Exception; }
    static boolean complete(JSONArray assets) {
        boolean apk = false, metadata = false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            apk |= "RGDS-Dashboard.apk".equals(asset.optString("name"));
            metadata |= "update.json".equals(asset.optString("name"));
        }
        return apk && metadata;
    }
    static JSONArray resolve(String repository, JSONObject release, Loader loader) throws Exception {
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null && complete(assets)) return assets;
        long id = release.optLong("id", -1);
        if (id <= 0) throw new IOException("Release sin identificador válido");
        // Construct the trusted endpoint; never follow an arbitrary assets_url.
        return new JSONArray(loader.load("https://api.github.com/repos/" + repository
                + "/releases/" + id + "/assets?per_page=100"));
    }
}
