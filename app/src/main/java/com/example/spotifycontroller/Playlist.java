package com.example.spotifycontroller;

public class Playlist {
    private String id;
    private String name;

    public Playlist(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getID() {
        return id;
    }

    public String getName() {
        return name;
    }

}
