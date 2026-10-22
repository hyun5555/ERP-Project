package com.erp.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LocalLlmClient {

	private final JsonMapper jsonMapper;
	private final HttpClient httpClient;
	private final URI generateUri;
	private final String model;
	private final boolean think;
	private final Duration timeout;

	public LocalLlmClient(JsonMapper jsonMapper,
			@Value("${erp.ai.base-url:http://localhost:11434}") String baseUrl,
			@Value("${erp.ai.model:qwen3.5:2b}") String model,
			@Value("${erp.ai.think:false}") boolean think,
			@Value("${erp.ai.timeout-seconds:60}") long timeoutSeconds) {
		if (timeoutSeconds < 1) throw new IllegalArgumentException("AI timeout must be positive");
		this.jsonMapper = jsonMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		this.generateUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/generate");
		this.model = model;
		this.think = think;
		this.timeout = Duration.ofSeconds(timeoutSeconds);
	}

	public GenerationStats generate(String prompt, Consumer<String> tokenConsumer)
			throws IOException, InterruptedException {
		long started = System.nanoTime();
		String requestBody = jsonMapper.writeValueAsString(Map.of(
				"model", model, "prompt", prompt, "stream", true, "think", think,
				"options", Map.of("temperature", 0.2)));
		HttpRequest request = HttpRequest.newBuilder(generateUri).timeout(timeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
		GenerationSubscriber subscriber = new GenerationSubscriber(started, tokenConsumer);
		CompletableFuture<HttpResponse<Void>> responseFuture = httpClient.sendAsync(request, info -> info.statusCode() == 200
				? HttpResponse.BodySubscribers.fromLineSubscriber(subscriber, s -> null, StandardCharsets.UTF_8, null)
				: HttpResponse.BodySubscribers.<Void>replacing(null));
		try {
			// 완료까지 기다리는 subscriber를 사용해야 헤더뿐 아니라 응답 본문 전체에 제한 시간이 적용된다.
			long remaining = Math.max(1, timeout.toNanos() - (System.nanoTime() - started));
			HttpResponse<Void> response = responseFuture.get(remaining, TimeUnit.NANOSECONDS);
			if (response.statusCode() != 200) {
				throw new IOException("Local LLM returned HTTP " + response.statusCode());
			}
			if (!subscriber.done) throw new IOException("Local LLM stream ended without done:true");
			if (subscriber.firstTokenMs < 0) throw new IOException("Local LLM returned an empty answer");
			return new GenerationStats(model, subscriber.firstTokenMs, elapsedMs(started), subscriber.evaluatedTokens);
		} catch (TimeoutException exception) {
			throw new HttpTimeoutException("Local LLM exceeded total response deadline");
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof UncheckedIOException unchecked) throw unchecked.getCause();
			if (cause instanceof IOException io) throw io;
			throw new IOException("Local LLM stream failed", cause);
		} finally {
			if (!responseFuture.isDone()) responseFuture.cancel(true);
		}
	}

	private class GenerationSubscriber implements Flow.Subscriber<String> {
		private final long started;
		private final Consumer<String> tokenConsumer;
		private long firstTokenMs = -1;
		private long evaluatedTokens;
		private boolean done;

		private GenerationSubscriber(long started, Consumer<String> tokenConsumer) {
			this.started = started;
			this.tokenConsumer = tokenConsumer;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }

		@Override
		public void onNext(String line) {
			JsonNode event = jsonMapper.readTree(line);
			JsonNode error = event.get("error");
			if (error != null) throw new UncheckedIOException(new IOException(error.asString()));
			if (done) throw new UncheckedIOException(new IOException("Local LLM sent data after done:true"));
			JsonNode text = event.get("response");
			if (text != null && !text.asString().isEmpty()) {
				String token = text.asString();
				if (firstTokenMs < 0 && !token.isBlank()) firstTokenMs = elapsedMs(started);
				tokenConsumer.accept(token);
			}
			JsonNode tokenCount = event.get("eval_count");
			if (tokenCount != null) evaluatedTokens = tokenCount.asLong();
			JsonNode completed = event.get("done");
			done = completed != null && completed.isBoolean() && completed.asBoolean();
		}

		@Override
		public void onError(Throwable error) {} // BodySubscriber가 응답 future에 실패를 전달한다.

		@Override
		public void onComplete() {}
	}

	private static long elapsedMs(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}

	public record GenerationStats(String model, long firstTokenMs, long durationMs, long evaluatedTokens) {}
}
