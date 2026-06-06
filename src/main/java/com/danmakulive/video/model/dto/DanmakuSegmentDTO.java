package com.danmakulive.video.model.dto;

public class DanmakuSegmentDTO {

    private String id;
    private String userId;
    private String userName;
    private String content;
    private Double playbackTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Double getPlaybackTime() { return playbackTime; }
    public void setPlaybackTime(Double playbackTime) { this.playbackTime = playbackTime; }
}
