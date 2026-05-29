// DTO/model for a favorite song linked to a tuning; it provides preference data serialized to/from the frontend.

package com.autotuner.backend.model;

public class FavoriteSongPreference {
    private String name;
    private String tuning;
    private String artist;

    public FavoriteSongPreference() {
    }

    public FavoriteSongPreference(String name, String tuning) {
        this.name = name;
        this.tuning = tuning;
    }

    public FavoriteSongPreference(String name, String tuning, String artist) {
        this.name = name;
        this.tuning = tuning;
        this.artist = artist;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTuning() {
        return tuning;
    }

    public void setTuning(String tuning) {
        this.tuning = tuning;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }
}
