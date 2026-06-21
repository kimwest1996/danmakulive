package com.danmakulive.danmaku.controller;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.common.result.Result;
import com.danmakulive.danmaku.model.dto.DanmakuHistoryDTO;
import com.danmakulive.danmaku.model.dto.DanmakuRequest;
import com.danmakulive.danmaku.model.entity.LiveDanmaku;
import com.danmakulive.danmaku.model.mapper.LiveDanmakuMapper;
import com.danmakulive.danmaku.service.DanmakuService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
public class DanmakuController {

    private final DanmakuService danmakuService;
    private final LiveDanmakuMapper liveDanmakuMapper;

    public DanmakuController(DanmakuService danmakuService, LiveDanmakuMapper liveDanmakuMapper) {
        this.danmakuService = danmakuService;
        this.liveDanmakuMapper = liveDanmakuMapper;
    }

    @PostMapping("/{roomId}/danmaku")
    public Result<Void> sendDanmaku(@PathVariable String roomId,
                                  @RequestBody DanmakuRequest request,
                                  HttpServletRequest httpRequest,
                                  @RequestParam(defaultValue = "false") boolean bypassRateLimit) {
        UserDTO user = UserHolder.getUser();
        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            return Result.failure("CLIENT_ERROR", "弹幕内容不能为空");
        }
        if (content.trim().length() > 200) {
            return Result.failure("CLIENT_ERROR", "弹幕内容过长");
        }

        String clientIp = httpRequest.getRemoteAddr();
        String error = danmakuService.processDanmaku(
                roomId, user.getId(), user.getNickName(), clientIp, content.trim(), bypassRateLimit);

        if (error != null) {
            return Result.failure("RATE_LIMITED", error);
        }
        return Result.success();
    }

    @GetMapping("/{roomId}/danmaku/history")
    public Result<HistoryPage> getHistory(@PathVariable String roomId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        IPage<LiveDanmaku> pageResult = liveDanmakuMapper.selectPage(
                new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LiveDanmaku>()
                        .eq(LiveDanmaku::getRoomId, roomId)
                        .orderByDesc(LiveDanmaku::getSendTime));

        List<DanmakuHistoryDTO> list = pageResult.getRecords().stream().map(dm -> {
            DanmakuHistoryDTO dto = new DanmakuHistoryDTO();
            dto.setId(dm.getId());
            dto.setUserId(dm.getUserId());
            dto.setUserName(dm.getUserName());
            dto.setContent(dm.getContent());
            dto.setSendTime(dm.getSendTime());
            return dto;
        }).collect(Collectors.toList());

        HistoryPage result = new HistoryPage();
        result.setData(list);
        result.setTotal(pageResult.getTotal());
        result.setPage((int) pageResult.getCurrent());
        result.setSize((int) pageResult.getSize());
        return Result.success(result);
    }

    public static class HistoryPage {
        private List<DanmakuHistoryDTO> data;
        private long total;
        private int page;
        private int size;

        public List<DanmakuHistoryDTO> getData() { return data; }
        public void setData(List<DanmakuHistoryDTO> data) { this.data = data; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
}
