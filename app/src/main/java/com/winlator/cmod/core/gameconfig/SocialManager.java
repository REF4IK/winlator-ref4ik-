package com.winlator.cmod.core.gameconfig;

import android.content.SharedPreferences;
import com.winlator.cmod.core.MmkvPreferences;
import org.json.JSONArray;

public class SocialManager {
    private static final String PREFS_NAME = "community_configs";
    private static final String VOTED_KEY = "voted_shas";
    private static final String FAV_KEY = "fav_shas";

    private final SharedPreferences prefs;

    public SocialManager() {
        this.prefs = new MmkvPreferences(PREFS_NAME);
    }

    public interface VoteCallback {
        void onResult(boolean success, int votesUp, int votesDown, String error);
    }

    public interface CommentsCallback {
        void onResult(JSONArray comments);
    }

    public boolean hasVoted(String sha) {
        String voted = prefs.getString(VOTED_KEY, "");
        return voted.contains(sha);
    }

    public boolean isFav(String sha) {
        if (sha == null || sha.isEmpty()) return false;
        String fav = prefs.getString(FAV_KEY, "");
        return ("," + fav + ",").contains("," + sha + ",");
    }

    public void toggleFav(String sha) {
        if (sha == null || sha.isEmpty()) return;
        String fav = prefs.getString(FAV_KEY, "");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String s : fav.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) set.add(s);
        }
        if (set.contains(sha)) set.remove(sha);
        else set.add(sha);
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        prefs.edit().putString(FAV_KEY, sb.toString()).apply();
    }

    public void vote(String sha, boolean upvote, VoteCallback callback) {
        if (hasVoted(sha)) {
            callback.onResult(false, 0, 0, "Already voted");
            return;
        }

        CloudConfigRepoV2.vote(sha, upvote, (success, up, down, error) -> {
            if (success) {
                String voted = prefs.getString(VOTED_KEY, "");
                prefs.edit().putString(VOTED_KEY, voted + "," + sha).apply();
            }
            callback.onResult(success, up, down, error);
        });
    }

    public void postComment(String sha, String text, String nickname, CloudConfigRepoV2.CommentCallback callback) {
        postComment(sha, text, nickname, null, callback);
    }

    public void postComment(String sha, String text, String nickname, String session, CloudConfigRepoV2.CommentCallback callback) {
        CloudConfigRepoV2.postComment(sha, text, nickname, session, callback);
    }

    public void getComments(String sha, CommentsCallback callback) {
        CloudConfigRepoV2.fetchComments(sha, callback::onResult);
    }
}
