package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.chat.ChatDtos.ContactVO;
import com.aies.dto.chat.ChatDtos.MessageVO;
import com.aies.dto.chat.ChatDtos.ReadResult;
import com.aies.dto.chat.ChatDtos.SendRequest;
import com.aies.security.PrincipalUtils;
import com.aies.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线聊天接口（V20）：会话列表、历史分页、发送、已读。
 *
 * <p>权限点 chat:use（学生/教师）；会话对象归属校验在 ChatService 内执行。
 * 实时投递走 /ws 的 chat.message 事件，本控制器只负责落库与查询。</p>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @PreAuthorize("hasAuthority('" + PermissionConstants.CHAT_USE + "')")
    @GetMapping("/contacts")
    public Result<List<ContactVO>> contacts() {
        return Result.success(chatService.contacts(PrincipalUtils.current()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CHAT_USE + "')")
    @GetMapping("/messages")
    public Result<List<MessageVO>> messages(
            @RequestParam("peerId") @Positive long peerId,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "size", defaultValue = "30") @Min(1) @Max(50) int size) {
        return Result.success(chatService.messages(PrincipalUtils.current(), peerId, beforeId, size));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CHAT_USE + "')")
    @PostMapping("/messages")
    public Result<MessageVO> send(@Valid @RequestBody SendRequest request) {
        return Result.success(chatService.send(PrincipalUtils.current(),
                request.receiverId(), request.content()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.CHAT_USE + "')")
    @PostMapping("/conversations/{peerId}/read")
    public Result<ReadResult> markRead(@PathVariable("peerId") @Positive long peerId) {
        return Result.success(new ReadResult(chatService.markRead(PrincipalUtils.current(), peerId)));
    }
}
