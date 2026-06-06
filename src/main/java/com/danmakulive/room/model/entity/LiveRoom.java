package com.danmakulive.room.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danmakulive.common.base.BaseDO;

import java.time.LocalDateTime;

@TableName("live_room")
public class LiveRoom extends BaseDO {

    private String id;
    private String title;
    private String ownerId;
    private Integer status;
    private String replayVideoId;
    private Integer replayStatus;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getReplayVideoId() { return replayVideoId; }
    public void setReplayVideoId(String replayVideoId) { this.replayVideoId = replayVideoId; }

    public Integer getReplayStatus() { return replayStatus; }
    public void setReplayStatus(Integer replayStatus) { this.replayStatus = replayStatus; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
