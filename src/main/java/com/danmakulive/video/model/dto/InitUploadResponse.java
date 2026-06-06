package com.danmakulive.video.model.dto;

import java.util.List;

public class InitUploadResponse {

    private String uploadId;
    private List<PresignedUrl> presignedUrls;
    private String bucketName;
    private String objectPrefix;

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }

    public List<PresignedUrl> getPresignedUrls() { return presignedUrls; }
    public void setPresignedUrls(List<PresignedUrl> presignedUrls) { this.presignedUrls = presignedUrls; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getObjectPrefix() { return objectPrefix; }
    public void setObjectPrefix(String objectPrefix) { this.objectPrefix = objectPrefix; }

    public static class PresignedUrl {
        private int partNumber;
        private String url;

        public PresignedUrl() {}
        public PresignedUrl(int partNumber, String url) {
            this.partNumber = partNumber;
            this.url = url;
        }

        public int getPartNumber() { return partNumber; }
        public void setPartNumber(int partNumber) { this.partNumber = partNumber; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
