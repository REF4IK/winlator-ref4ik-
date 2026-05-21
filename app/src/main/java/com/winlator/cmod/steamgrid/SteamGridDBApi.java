package com.winlator.cmod.steamgrid;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SteamGridDBApi {

    @GET("search/autocomplete/{term}")
    Call<SteamGridSearchResponse> searchGame(
            @Header("Authorization") String authHeader,
            @Path("term") String searchTerm
    );

    @GET("grids/game/{gameId}")
    Call<SteamGridGridsResponse> getGridsByGameId(
            @Header("Authorization") String authToken,
            @Path("gameId") int gameId,
            @Query("styles") String styles,
            @Query("dimensions") String dimensions,
            @Query("types") String types
    );
}
