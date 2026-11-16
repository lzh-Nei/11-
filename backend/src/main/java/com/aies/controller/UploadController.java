package com.aies.controller;

import com.aies.model.BatchItemResult;
import com.aies.model.ChunkResult;
import com.aies.model.CompleteResponse;
import com.aies.model.InitResponse;
import com.aies.service.FileStoreService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 上传相关接口（S-06：写接口@PreAuthorize，权限点 PermissionConstants.SUBMISSION_UPLOAD）。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final FileStoreService store;

    public UploadController(FileStoreService store) {
        this.store = store;
    }

    /** 初始化分片上传：返回 uploadId 与服务端已有的分片列表（断点续传依据） */
    @PreAuthorize("hasAuthority('submission:upload')")
    @PostMapping("/init")
    public InitResponse init(@RequestParam String fileName,
                             @RequestParam long fileSize,
                             @RequestParam long lastModified,
                             @RequestParam long chunkSize,
                             @RequestParam int totalChunks) throws IOException {
        return store.initUpload(fileName, fileSize, lastModified, chunkSize, totalChunks);
    }

    /** 上传单个分片（服务端边收边算 MD5 并落盘记录） */
    @PreAuthorize("hasAuthority('submission:upload')")
    @PostMapping("/chunk")
    public ChunkResult chunk(@RequestParam String uploadId,
                             @RequestParam int chunkIndex,
                             @RequestParam("file") MultipartFile file) throws IOException {
        return store.saveChunk(uploadId, chunkIndex, file);
    }

    /** 请求合并：若存在损坏分片，返回 badChunks 通知客户端定向重传 */
    @PreAuthorize("hasAuthority('submission:upload')")
    @PostMapping("/complete")
    public CompleteResponse complete(@RequestParam String uploadId) throws IOException {
        return store.complete(uploadId);
    }

    /** 小文件批量上传：一个请求多个文件，逐个落盘、逐个返回结果 */
    @PreAuthorize("hasAuthority('submission:upload')")
    @PostMapping("/batch")
    public List<BatchItemResult> batch(@RequestParam("files") MultipartFile[] files) throws IOException {
        return store.batchUpload(files);
    }

    /** 演示用：人为破坏一个已上传分片，用于展示"合并时检测坏块 → 通知重传"闭环 */
    @PreAuthorize("hasAuthority('submission:upload')")
    @PostMapping("/corrupt-chunk")
    public ChunkResult corrupt(@RequestParam String uploadId) throws IOException {
        return store.corruptRandomChunk(uploadId);
    }
}
