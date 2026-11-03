package com.group11.aievaluate.controller;

import com.group11.aievaluate.model.FileMeta;
import com.group11.aievaluate.service.FileStoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 文件列表查询。
 */
@RestController
public class FileController {

    private final FileStoreService store;

    public FileController(FileStoreService store) {
        this.store = store;
    }

    /** 已入库文件列表（前端下载中心使用） */
    @GetMapping("/api/files")
    public List<FileMeta> files() throws IOException {
        return store.listFiles();
    }
}
