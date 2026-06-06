package com.danmakulive.auth;

import com.baomidou.mybatisplus.annotation.TableName;
import com.danmakulive.common.BaseDO;

@TableName("user")
public class User extends BaseDO {

    private String id;
    private String email;
    private String password;
    private String nickname;
    private String avatarUrl;
    private Integer status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
