package com.danmakulive.video.upload.model.dto;

public class MergeResponse {

    private String videoId;

    public MergeResponse() {}

    public MergeResponse(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
}
