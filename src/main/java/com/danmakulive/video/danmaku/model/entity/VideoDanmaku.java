package com.danmakulive.video.danmaku.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danmakulive.common.base.BaseDO;

@TableName("video_danmaku")
public class VideoDanmaku extends BaseDO {

    private String id;
    private String videoId;
    private String userId;
    private String userName;
    private String content;
    private Double playbackTime;
    private Long sendTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Double getPlaybackTime() { return playbackTime; }
    public void setPlaybackTime(Double playbackTime) { this.playbackTime = playbackTime; }

    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
}
