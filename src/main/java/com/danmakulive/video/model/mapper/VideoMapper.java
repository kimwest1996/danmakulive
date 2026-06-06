package com.danmakulive.video.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmakulive.video.model.entity.Video;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
}
