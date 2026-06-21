package com.danmakulive.danmaku.pipeline;

import com.danmakulive.danmaku.model.DanmakuMessage;

public class PipelineContext {

    public static final String SCENE_LIVE = "LIVE";
    public static final String SCENE_VIDEO = "VIDEO";

    private String scene;
    private String roomId;
    private String videoId;
    private Double playbackTime;
    private String userId;
    private String userName;
    private String clientIp;
    private String rawContent;
    private String filteredContent;
    private DanmakuMessage message;
    private String error;
    private boolean bypassRateLimit;

    public boolean hasError() {
        return error != null;
    }

    public boolean isLive() { return SCENE_LIVE.equals(scene); }
    public boolean isVideo() { return SCENE_VIDEO.equals(scene); }

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public Double getPlaybackTime() { return playbackTime; }
    public void setPlaybackTime(Double playbackTime) { this.playbackTime = playbackTime; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getFilteredContent() { return filteredContent; }
    public void setFilteredContent(String filteredContent) { this.filteredContent = filteredContent; }

    public DanmakuMessage getMessage() { return message; }
    public void setMessage(DanmakuMessage message) { this.message = message; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean isBypassRateLimit() { return bypassRateLimit; }
    public void setBypassRateLimit(boolean bypassRateLimit) { this.bypassRateLimit = bypassRateLimit; }
}
