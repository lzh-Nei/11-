package com.aies.controller;

import com.aies.common.Result;
import com.aies.model.FileMetaVO;
import com.aies.security.PrincipalUtils;
import com.aies.service.FileCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 文件中心：列表附带上传者姓名/班级，管理员可移除。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileCenterService fileCenterService;

    /** 已入库文件列表（仅教师/管理员）。 */
    @GetMapping("/api/files")
    public List<FileMetaVO> files() throws IOException {
        return fileCenterService.listFiles(PrincipalUtils.current());
    }

    /** 管理员删除文件中心文件（带审计）。 */
    @DeleteMapping("/api/files/{id}")
    public Result<Void> delete(@PathVariable("id") String id) throws IOException {
        fileCenterService.delete(id, PrincipalUtils.current());
        return Result.success();
    }
}
