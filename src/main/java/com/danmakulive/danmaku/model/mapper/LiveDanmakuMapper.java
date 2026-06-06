package com.danmakulive.danmaku.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danmakulive.danmaku.model.entity.LiveDanmaku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LiveDanmakuMapper extends BaseMapper<LiveDanmaku> {
}
