// DTO holding all saved preferences for one user; it provides preference JSON for load/save endpoints.

package com.autotuner.backend.model;

import java.util.ArrayList;
import java.util.List;

public class UserPreferences {
    private List<String> preferredTuningKeys = new ArrayList<>();
    private List<FavoriteSongPreference> favoriteSongs = new ArrayList<>();

    public UserPreferences() {
    }

    public UserPreferences(List<String> preferredTuningKeys, List<FavoriteSongPreference> favoriteSongs) {
        this.preferredTuningKeys = preferredTuningKeys;
        this.favoriteSongs = favoriteSongs;
    }

    public List<String> getPreferredTuningKeys() {
        return preferredTuningKeys;
    }

    public void setPreferredTuningKeys(List<String> preferredTuningKeys) {
        this.preferredTuningKeys = preferredTuningKeys;
    }

    public List<FavoriteSongPreference> getFavoriteSongs() {
        return favoriteSongs;
    }

    public void setFavoriteSongs(List<FavoriteSongPreference> favoriteSongs) {
        this.favoriteSongs = favoriteSongs;
    }
}
