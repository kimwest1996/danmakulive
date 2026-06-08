package com.danmakulive.video.upload.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmakulive.video.upload.model.entity.VideoUpload;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoUploadMapper extends BaseMapper<VideoUpload> {
}
