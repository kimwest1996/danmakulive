package com.danmakulive.auth.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmakulive.auth.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
