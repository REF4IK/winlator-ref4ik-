package com.winlator.cmod.update;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface UpdateService {
    @GET("repos/{owner}/{repo}/releases/latest")
    Call<GitHubRelease> getLatestRelease(
        @Path("owner") String owner,
        @Path("repo") String repo
    );

    @GET("repos/{owner}/{repo}/releases")
    Call<List<GitHubRelease>> getAllReleases(
        @Path("owner") String owner,
        @Path("repo") String repo
    );
}
