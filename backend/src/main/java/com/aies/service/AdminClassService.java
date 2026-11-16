package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.admin.AdminDtos.AdminClassVO;
import com.aies.dto.admin.AdminDtos.ClassCreateRequest;
import com.aies.dto.admin.AdminDtos.ClassUpdateRequest;
import com.aies.entity.SysClass;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M3 管理端班级模块（UC-34/35）：班级列表、创建、改名/换教师、删除。
 *
 * <p>班级码全局唯一（注册 A-08 依据）；授课教师必须持有 TEACHER 角色；
 * 班级下仍有学生或任务时禁止删除（防悬挂外键）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminClassService {

    private static final String ROLE_TEACHER = "TEACHER";

    private final SysClassMapper classMapper;
    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final RbacMapper rbacMapper;
    private final AdminAuditService auditService;

    public Page<AdminClassVO> list(long page, long size) {
        Page<SysClass> result = classMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<SysClass>().orderByAsc(SysClass::getId));
        Map<Long, String> teacherNames = teacherNames(result.getRecords());
        List<AdminClassVO> records = result.getRecords().stream()
                .map(item -> toVO(item, teacherNames))
                .toList();
        Page<AdminClassVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Transactional
    public AdminClassVO create(ClassCreateRequest request, LoginPrincipal principal) {
        String classCode = request.getClassCode().trim();
        String className = request.getClassName().trim();
        requireTeacher(request.getTeacherId());
        long exists = classMapper.selectCount(new LambdaQueryWrapper<SysClass>()
                .eq(SysClass::getClassCode, classCode));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "班级码已存在：" + classCode);
        }
        SysClass entity = new SysClass();
        entity.setClassName(className);
        entity.setClassCode(classCode);
        entity.setTeacherId(request.getTeacherId());
        classMapper.insert(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("className", className);
        detail.put("classCode", classCode);
        detail.put("teacherId", request.getTeacherId());
        auditService.record(principal, "system", "CREATE_CLASS", "class:" + entity.getId(), detail);

        return toVO(entity, teacherNames(List.of(entity)));
    }

    @Transactional
    public AdminClassVO update(long id, ClassUpdateRequest request, LoginPrincipal principal) {
        SysClass entity = requireClass(id);
        requireTeacher(request.getTeacherId());
        String oldName = entity.getClassName();
        Long oldTeacher = entity.getTeacherId();
        classMapper.update(null, new UpdateWrapper<SysClass>()
                .eq("id", id)
                .set("class_name", request.getClassName().trim())
                .set("teacher_id", request.getTeacherId()));

        entity.setClassName(request.getClassName().trim());
        entity.setTeacherId(request.getTeacherId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("className", Map.of("before", oldName, "after", entity.getClassName()));
        detail.put("teacherId", Map.of("before", oldTeacher, "after", entity.getTeacherId()));
        auditService.record(principal, "system", "UPDATE_CLASS", "class:" + id, detail);

        return toVO(entity, teacherNames(List.of(entity)));
    }

    @Transactional
    public void delete(long id, LoginPrincipal principal) {
        SysClass entity = requireClass(id);
        long students = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getClassId, id));
        if (students > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "班级下仍有 " + students + " 名学生，不能删除");
        }
        long tasks = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getClassId, id));
        if (tasks > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "班级下仍有 " + tasks + " 个任务，不能删除");
        }
        classMapper.deleteById(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("className", entity.getClassName());
        detail.put("classCode", entity.getClassCode());
        auditService.record(principal, "system", "DELETE_CLASS", "class:" + id, detail);
    }

    private SysClass requireClass(long id) {
        SysClass entity = classMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级不存在");
        }
        return entity;
    }

    private void requireTeacher(Long teacherId) {
        if (teacherId == null || !rbacMapper.selectRoleCodes(teacherId).contains(ROLE_TEACHER)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "授课教师不存在或未授予 TEACHER 角色");
        }
    }

    private AdminClassVO toVO(SysClass entity, Map<Long, String> teacherNames) {
        long studentCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getClassId, entity.getId()));
        long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getClassId, entity.getId()));
        return new AdminClassVO(entity.getId(), entity.getClassName(), entity.getClassCode(),
                entity.getTeacherId(), teacherNames.get(entity.getTeacherId()),
                studentCount, taskCount, entity.getCreateTime());
    }

    private Map<Long, String> teacherNames(List<SysClass> classes) {
        List<Long> ids = classes.stream().map(SysClass::getTeacherId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> StringUtils.hasText(user.getRealName())
                                ? user.getRealName() : user.getUsername(),
                        (left, right) -> left));
    }
}
