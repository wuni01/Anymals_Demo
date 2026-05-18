package com.example.anymals_demo;

public class PinModel {
    public String pinId;
    public String uid;
    public String comment;
    public double lat;
    public double lng;
    public String imageUrl;
    public long timestamp;

    public PinModel() {}

    public PinModel(String pinId, String uid, String comment, double lat, double lng, String imageUrl, long timestamp) {
        this.pinId = pinId;
        this.uid = uid;
        this.comment = comment;
        this.lat = lat;
        this.lng = lng;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
    }
}