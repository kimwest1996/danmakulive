package com.danmakulive.video.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danmakulive.common.base.BaseDO;

@TableName("video")
public class Video extends BaseDO {

    private String id;
    private String title;
    private Integer duration;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}
