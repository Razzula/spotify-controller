package com.example.spotifycontroller;

import android.graphics.Bitmap;

public class Playlist {
    private String id;
    private String name;
    private String description;
    private int numberOfTracks;
    private Bitmap image;

    public Playlist(String id, String name, String description, int numberOfTracks) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.numberOfTracks = numberOfTracks;
        this.image = null;
    }

    public Playlist(String id, String name, String description, int numberOfTracks, Bitmap image) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.numberOfTracks = numberOfTracks;
        this.image = image;
    }

    public String getID() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public int getNumberOfTracks() {
        return numberOfTracks;
    }
    public Bitmap getImage() { return image; }
}