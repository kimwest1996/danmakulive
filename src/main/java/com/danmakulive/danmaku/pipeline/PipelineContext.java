package com.danmakulive.danmaku.pipeline;

import com.danmakulive.danmaku.model.DanmakuMessage;

public class PipelineContext {

    private String roomId;
    private String userId;
    private String userName;
    private String clientIp;
    private String rawContent;
    private String filteredContent;
    private DanmakuMessage message;
    private String error;

    public boolean hasError() {
        return error != null;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

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
}
