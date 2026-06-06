package com.danmakulive.room.model;

public class StreamEndedEvent {

    private String type;
    private String roomId;
    private String videoId;

    public StreamEndedEvent() {}

    public StreamEndedEvent(String roomId, String videoId) {
        this.type = "STREAM_ENDED";
        this.roomId = roomId;
        this.videoId = videoId;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
}
