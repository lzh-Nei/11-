package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.entity.Submission;
import com.aies.entity.SysClass;
import com.aies.entity.User;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.UserMapper;
import com.aies.model.FileMeta;
import com.aies.model.FileMetaVO;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件中心聚合：文件列表补上传者姓名/班级，管理员移除文件。
 */
@Service
@RequiredArgsConstructor
public class FileCenterService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_ADMIN = "ADMIN";

    private final FileStoreService store;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final SysClassMapper classMapper;
    private final AdminAuditService auditService;

    /** 已入库文件列表（仅教师/管理员；上传者优先取上传元数据，历史文件回查提交记录）。 */
    public List<FileMetaVO> listFiles(LoginPrincipal principal) throws IOException {
        requireStaff(principal);
        List<FileMeta> files = store.listFiles();
        if (files.isEmpty()) {
            return List.of();
        }

        Set<String> fileKeys = files.stream().map(FileMeta::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Long> submissionUploaders = new HashMap<>();
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .in(Submission::getFileKey, fileKeys));
        if (submissions != null) {
            for (Submission submission : submissions) {
                if (submission.getFileKey() != null && submission.getStudentId() != null) {
                    submissionUploaders.putIfAbsent(submission.getFileKey(), submission.getStudentId());
                }
            }
        }

        Set<Long> uploaderIds = files.stream()
                .map(meta -> meta.uploaderId() != null
                        ? meta.uploaderId() : submissionUploaders.get(meta.id()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> users = uploaderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(new ArrayList<>(uploaderIds)).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(),
                                (left, right) -> left));
        Set<Long> classIds = users.values().stream()
                .map(User::getClassId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysClass> classes = classIds.isEmpty() ? Map.of()
                : classMapper.selectBatchIds(new ArrayList<>(classIds)).stream()
                        .collect(Collectors.toMap(SysClass::getId, Function.identity(),
                                (left, right) -> left));

        List<FileMetaVO> result = new ArrayList<>();
        for (FileMeta meta : files) {
            Long uploaderId = meta.uploaderId() != null
                    ? meta.uploaderId() : submissionUploaders.get(meta.id());
            User user = uploaderId == null ? null : users.get(uploaderId);
            SysClass clazz = (user == null || user.getClassId() == null)
                    ? null : classes.get(user.getClassId());
            String uploaderName = user == null ? null
                    : (user.getRealName() == null || user.getRealName().isBlank()
                            ? user.getUsername() : user.getRealName());
            result.add(new FileMetaVO(meta.id(), meta.name(), meta.size(), meta.uploadTime(),
                    uploaderId, uploaderName, clazz == null ? null : clazz.getClassName()));
        }
        return result;
    }

    /** 管理员删除/移除文件：被提交引用的文件软删除，保留原件避免下载链接失效。 */
    public void delete(String id, LoginPrincipal principal) throws IOException {
        if (!ROLE_ADMIN.equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        FileMeta meta = store.getMeta(id);
        long referenced = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getFileKey, id));
        String action;
        if (referenced > 0) {
            store.hide(id);
            action = "HIDE_FILE";
        } else {
            store.delete(id);
            action = "DELETE_FILE";
        }
        auditService.record(principal, "system", action, "file:" + id,
                Map.of("fileName", meta.name(), "size", meta.size(), "referenced", referenced > 0));
    }

    private void requireStaff(LoginPrincipal principal) {
        if (ROLE_STUDENT.equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
