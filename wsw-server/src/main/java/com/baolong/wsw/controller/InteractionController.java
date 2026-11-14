package com.baolong.wsw.controller;

import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.InteractionDto;
import com.baolong.wsw.entity.BargainRecord;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.FileService;
import com.baolong.wsw.service.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 议价、举报、文件上传。
 * <p>这三类接口都不参与任务状态机，所以从 TaskController / ForumController 里独立出来。
 */
@RestController
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;
    private final FileService fileService;

    // ------------------------------------------------------------------ 议价

    /** 我会报价 */
    @PostMapping("/tasks/{id}/bargain")
    public Result<BargainRecord> bargain(@PathVariable Long id,
                                         @RequestBody InteractionDto.BargainReq req) {
        int amount = req.amount() == null ? 0 : req.amount();
        return Result.ok(interactionService.offer(id, AuthContext.requireUserId(), amount, req.message()));
    }

    /** 发布人接受 / 拒绝报价 */
    @PostMapping("/tasks/{id}/bargain/respond")
    public Result<Void> respond(@PathVariable Long id,
                                @RequestBody InteractionDto.BargainRespondReq req) {
        interactionService.respond(id, AuthContext.requireUserId(),
                req.bargainId(), req.accept(), req.remark());
        return Result.ok(null);
    }

    /** 任务相关方查看报价记录 */
    @GetMapping("/tasks/{id}/bargains")
    public Result<List<InteractionDto.BargainVO>> bargains(@PathVariable Long id) {
        return Result.ok(interactionService.list(id, AuthContext.userIdOrNull()));
    }

    // ------------------------------------------------------------------ 举报

    @PostMapping("/tasks/{id}/report")
    public Result<Void> reportTask(@PathVariable Long id,
                                   @RequestBody InteractionDto.ReportReq req) {
        interactionService.reportTask(id, AuthContext.requireUserId(),
                req.type(), req.reason(), req.evidence());
        return Result.ok(null);
    }

    @PostMapping("/posts/{id}/report")
    public Result<Void> reportPost(@PathVariable Long id,
                                   @RequestBody InteractionDto.ReportReq req) {
        interactionService.reportPost(id, AuthContext.requireUserId(),
                req.type(), req.reason(), req.evidence());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 上传

    @PostMapping("/files/upload")
    public Result<InteractionDto.UploadVO> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.store(file);
        return Result.ok(new InteractionDto.UploadVO(url,
                file.getOriginalFilename(), file.getSize()));
    }
}
