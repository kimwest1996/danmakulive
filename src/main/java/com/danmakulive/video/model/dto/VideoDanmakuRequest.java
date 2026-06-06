package com.danmakulive.video.model.dto;

public class VideoDanmakuRequest {

    private String content;
    private Double playbackTime;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Double getPlaybackTime() { return playbackTime; }
    public void setPlaybackTime(Double playbackTime) { this.playbackTime = playbackTime; }
}
