package com.danmakulive.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import com.danmakulive.danmaku.pipeline.DanmakuPipeline;
import com.danmakulive.danmaku.pipeline.PipelineContext;
import com.danmakulive.video.model.dto.DanmakuSegmentDTO;
import com.danmakulive.video.model.dto.DensityDTO;
import com.danmakulive.video.model.entity.VideoDanmaku;
import com.danmakulive.video.model.entity.Video;
import com.danmakulive.video.model.mapper.VideoDanmakuMapper;
import com.danmakulive.video.model.mapper.VideoMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoDanmakuService {

    private static final int SEGMENT_SECONDS = 60;

    private final DanmakuPipeline pipeline;
    private final VideoDanmakuMapper danmakuMapper;
    private final VideoMapper videoMapper;

    public VideoDanmakuService(DanmakuPipeline pipeline,
                                VideoDanmakuMapper danmakuMapper,
                                VideoMapper videoMapper) {
        this.pipeline = pipeline;
        this.danmakuMapper = danmakuMapper;
        this.videoMapper = videoMapper;
    }

    public String sendDanmaku(String videoId, String userId, String userName,
                               String clientIp, String content, Double playbackTime) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }
        PipelineContext ctx = new PipelineContext();
        ctx.setScene(PipelineContext.SCENE_VIDEO);
        ctx.setVideoId(videoId);
        ctx.setUserId(userId);
        ctx.setUserName(userName);
        ctx.setClientIp(clientIp);
        ctx.setRawContent(content);
        ctx.setPlaybackTime(playbackTime);

        pipeline.execute(ctx);
        return ctx.getError();
    }

    public List<DanmakuSegmentDTO> getSegments(String videoId, double from, double to) {
        List<VideoDanmaku> list = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .ge(VideoDanmaku::getPlaybackTime, from)
                        .lt(VideoDanmaku::getPlaybackTime, to)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        return list.stream().map(dm -> {
            DanmakuSegmentDTO dto = new DanmakuSegmentDTO();
            dto.setId(dm.getId());
            dto.setUserId(dm.getUserId());
            dto.setUserName(dm.getUserName());
            dto.setContent(dm.getContent());
            dto.setPlaybackTime(dm.getPlaybackTime());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<DensityDTO> getDensity(String videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new ClientException("视频不存在", BaseErrorCode.NOT_FOUND);
        }

        List<VideoDanmaku> all = danmakuMapper.selectList(
                new LambdaQueryWrapper<VideoDanmaku>()
                        .eq(VideoDanmaku::getVideoId, videoId)
                        .orderByAsc(VideoDanmaku::getPlaybackTime));

        int segments = (video.getDuration() / SEGMENT_SECONDS) + 1;
        int[] counts = new int[segments];
        for (VideoDanmaku dm : all) {
            int seg = (int) (dm.getPlaybackTime() / SEGMENT_SECONDS);
            if (seg < segments) {
                counts[seg]++;
            }
        }

        List<DensityDTO> result = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            if (counts[i] > 0) {
                result.add(new DensityDTO(i * SEGMENT_SECONDS, counts[i]));
            }
        }
        return result;
    }
}
