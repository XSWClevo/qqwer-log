package cn.mw.loganalysis.agent.controller;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentEmailRequest;
import cn.mw.loganalysis.agent.dto.AgentEmailResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import cn.mw.loganalysis.agent.dto.AgentVectorComponentCommitRequest;
import cn.mw.loganalysis.agent.service.AgentEmailService;
import cn.mw.loganalysis.agent.service.LogAnalysisAgentService;
import cn.mw.loganalysis.agent.service.AgentStreamWriter;
import cn.mw.loganalysis.agent.service.VectorComponentPlanToolHandler;
import cn.mw.loganalysis.common.exception.UnauthorizedException;
import cn.mw.loganalysis.common.response.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * 智能助手控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final LogAnalysisAgentService logAnalysisAgentService;
    private final AgentEmailService agentEmailService;
    private final VectorComponentPlanToolHandler vectorComponentPlanToolHandler;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request, Authentication authentication) {
        return Result.success(logAnalysisAgentService.chat(request, requireUserId(authentication)));
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> chatStream(@Valid @RequestBody AgentChatRequest request,
                                                            Authentication authentication) {
        Long userId = requireUserId(authentication);
        StreamingResponseBody body = outputStream -> {
            AgentStreamWriter writer = new AgentStreamWriter(objectMapper, outputStream);
            try {
                logAnalysisAgentService.streamChat(request, userId, writer);
            } catch (Exception ex) {
                log.error("智能助手流式处理失败, datasourceId={}, message={}", request.getDatasourceId(), request.getMessage(), ex);
                writer.emit(AgentStreamEvent.error("智能助手流式处理失败: " + ex.getMessage(), request.getSessionId()));
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue())
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    @GetMapping("/conversations")
    public Result<List<AgentConversationSummary>> listConversations(Authentication authentication) {
        return Result.success(logAnalysisAgentService.listConversations(requireUserId(authentication)));
    }

    @GetMapping("/conversations/{sessionId}")
    public Result<AgentConversationDetail> getConversation(@PathVariable String sessionId, Authentication authentication) {
        AgentConversationDetail detail = logAnalysisAgentService.getConversation(requireUserId(authentication), sessionId);
        if (detail == null) {
            return Result.notFound("历史对话不存在或已被删除");
        }
        return Result.success(detail);
    }

    @DeleteMapping("/conversations/{sessionId}")
    public Result<Void> deleteConversation(@PathVariable String sessionId, Authentication authentication) {
        logAnalysisAgentService.deleteConversation(requireUserId(authentication), sessionId);
        return Result.success();
    }

    @PostMapping("/email")
    public Result<AgentEmailResponse> sendEmail(@Valid @RequestBody AgentEmailRequest request, Authentication authentication) {
        return Result.success(agentEmailService.sendToCurrentUser(requireUserId(authentication), request));
    }

    @PostMapping("/vector-component-plans/{planId}/commit")
    public Result<AgentChatResponse> commitVectorComponentPlan(@PathVariable String planId,
                                                               @Valid @RequestBody AgentVectorComponentCommitRequest request,
                                                               Authentication authentication) {
        return Result.success(vectorComponentPlanToolHandler.commit(planId, requireUserId(authentication), request.getSessionId()));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedException("智能助手请求未识别到登录态，请重新登录后重试");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("智能助手请求未识别到登录用户，请重新登录后重试");
    }
}
