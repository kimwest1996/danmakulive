package com.danmakulive.auth.model.dto;

public class UserDTO {

    private String id;
    private String nickName;
    private String avatarUrl;

    public UserDTO() {}

    public UserDTO(String id, String nickName, String avatarUrl) {
        this.id = id;
        this.nickName = nickName;
        this.avatarUrl = avatarUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
