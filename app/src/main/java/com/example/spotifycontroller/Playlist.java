package com.example.spotifycontroller;

public class Playlist {
    private String id;
    private String name;
    private String description;
    private int numberOfTracks;

    public Playlist(String id, String name, String description, int numberOfTracks) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.numberOfTracks = numberOfTracks;
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
}
