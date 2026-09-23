package com.rgds.dashboard;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateAssetsTest {
    private JSONArray files() throws Exception {
        return new JSONArray().put(new JSONObject().put("name", "RGDS-Dashboard.apk"))
                .put(new JSONObject().put("name", "update.json"));
    }
    @Test public void recoversEmptyEmbeddedListingFromTrustedAssetEndpoint() throws Exception {
        JSONObject release = new JSONObject().put("id", 394421754).put("assets", new JSONArray())
                .put("assets_url", "https://untrusted.invalid/assets");
        JSONArray result = UpdateAssets.resolve("owner/repo", release, url -> {
            assertEquals("https://api.github.com/repos/owner/repo/releases/394421754/assets?per_page=100", url);
            return files().toString();
        });
        assertTrue(UpdateAssets.complete(result));
    }
    @Test public void alsoRecoversPartiallyPopulatedListing() throws Exception {
        JSONObject release = new JSONObject().put("id", 1).put("assets",
                new JSONArray().put(new JSONObject().put("name", "update.json")));
        assertTrue(UpdateAssets.complete(UpdateAssets.resolve("owner/repo", release, url -> files().toString())));
    }
    @Test public void completeListingDoesNotRequestAgain() throws Exception {
        JSONObject release = new JSONObject().put("assets", files());
        assertTrue(UpdateAssets.complete(UpdateAssets.resolve("owner/repo", release, url -> {
            fail("Should not fetch again"); return "[]";
        })));
    }
    @Test public void missingAndMalformedResponsesDoNotBecomeAnUpdate() throws Exception {
        JSONObject release = new JSONObject().put("id", 1);
        assertFalse(UpdateAssets.complete(UpdateAssets.resolve("owner/repo", release, url -> "[]")));
        try { UpdateAssets.resolve("owner/repo", release, url -> "not JSON"); fail(); }
        catch (JSONException expected) { }
        try { UpdateAssets.resolve("owner/repo", new JSONObject(), url -> { fail(); return "[]"; }); fail(); }
        catch (java.io.IOException expected) { }
    }
}
