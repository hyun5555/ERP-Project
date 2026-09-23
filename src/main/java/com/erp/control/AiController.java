package com.erp.control;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erp.service.AiService;
import com.erp.service.LocalLlmClient.GenerationStats;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api/ai")
public class AiController {

	private static final Logger log = LoggerFactory.getLogger(AiController.class);

	private final AiService aiService;
	private final long emitterTimeoutMs;

	public AiController(AiService aiService,
			@Value("${erp.ai.timeout-seconds:60}") long timeoutSeconds) {
		this.aiService = aiService;
		this.emitterTimeoutMs = (timeoutSeconds + 5) * 1_000;
	}

	@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chat(@RequestBody ChatRequest request, HttpSession session) {
		String question = AiService.validateQuestion(request.question());
		userVO user = (userVO) session.getAttribute("loginUser");
		SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
		Thread worker = Thread.ofVirtual().name("local-ai-" + user.getUsernum()).unstarted(() -> {
			try {
				GenerationStats stats = aiService.answer(user, question,
						token -> send(emitter, "token", Map.of("text", token)));
				send(emitter, "done", Map.of(
						"model", stats.model(),
						"firstTokenMs", stats.firstTokenMs(),
						"durationMs", stats.durationMs(),
						"evaluatedTokens", stats.evaluatedTokens()));
				emitter.complete();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} catch (Exception exception) {
				log.warn("local_ai_failed user={} reason={}", user.getUsernum(), exception.toString());
				try {
					send(emitter, "error", Map.of("message",
							"로컬 AI가 응답하지 않습니다. 모델 실행 상태를 확인해 주세요."));
				} catch (RuntimeException ignored) {
					// 연결이 이미 끊긴 경우 추가 전송 없이 종료한다.
				}
				emitter.complete();
			}
		});
		emitter.onTimeout(worker::interrupt);
		worker.start();
		return emitter;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("message", exception.getMessage()));
	}

	private static void send(SseEmitter emitter, String name, Object data) {
		try {
			emitter.send(SseEmitter.event().name(name).data(data));
		} catch (IOException exception) {
			throw new IllegalStateException("AI stream disconnected", exception);
		}
	}

	public record ChatRequest(String question) {}
}
