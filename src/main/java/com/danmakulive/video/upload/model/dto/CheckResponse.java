package com.danmakulive.video.upload.model.dto;

public class CheckResponse {

    private boolean exists;
    private boolean uploading;
    private String uploadId;
    private String videoId;

    public static CheckResponse notFound() {
        CheckResponse r = new CheckResponse();
        r.exists = false;
        return r;
    }

    public static CheckResponse uploading(String uploadId) {
        CheckResponse r = new CheckResponse();
        r.exists = true;
        r.uploading = true;
        r.uploadId = uploadId;
        return r;
    }

    public static CheckResponse uploaded(String videoId) {
        CheckResponse r = new CheckResponse();
        r.exists = true;
        r.uploading = false;
        r.videoId = videoId;
        return r;
    }

    public boolean isExists() { return exists; }
    public void setExists(boolean exists) { this.exists = exists; }

    public boolean isUploading() { return uploading; }
    public void setUploading(boolean uploading) { this.uploading = uploading; }

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
}
