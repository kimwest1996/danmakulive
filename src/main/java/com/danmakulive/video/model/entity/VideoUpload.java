package com.danmakulive.video.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danmakulive.common.base.BaseDO;

@TableName("video_upload")
public class VideoUpload extends BaseDO {

    private String id;
    private String fileHash;
    private String fileName;
    private Long fileSize;
    private Integer chunkCount;
    private Integer status;
    private String videoId;
    private String bucketName;
    private String objectPath;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getObjectPath() { return objectPath; }
    public void setObjectPath(String objectPath) { this.objectPath = objectPath; }
}
