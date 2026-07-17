package com.winlator.cmod.core.gameconfig;

import android.content.SharedPreferences;
import com.winlator.cmod.core.MmkvPreferences;
import org.json.JSONArray;

public class SocialManager {
    private static final String PREFS_NAME = "community_configs";
    private static final String VOTED_KEY = "voted_shas";

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
        CloudConfigRepoV2.postComment(sha, text, nickname, callback);
    }

    public void getComments(String sha, CommentsCallback callback) {
        CloudConfigRepoV2.fetchComments(sha, callback::onResult);
    }
}
