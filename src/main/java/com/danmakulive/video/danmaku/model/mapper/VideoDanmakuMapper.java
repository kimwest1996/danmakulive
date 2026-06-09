package com.danmakulive.video.danmaku.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmakulive.video.danmaku.model.dto.DensityDTO;
import com.danmakulive.video.danmaku.model.entity.VideoDanmaku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoDanmakuMapper extends BaseMapper<VideoDanmaku> {

    @Select("SELECT FLOOR(playback_time / 60) * 60 AS segment, COUNT(*) AS count " +
            "FROM video_danmaku WHERE video_id = #{videoId} " +
            "GROUP BY FLOOR(playback_time / 60) ORDER BY segment")
    List<DensityDTO> selectDensity(String videoId);
}
