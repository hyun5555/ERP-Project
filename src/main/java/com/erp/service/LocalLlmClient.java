package com.erp.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
	private final Duration timeout;

	public LocalLlmClient(JsonMapper jsonMapper,
			@Value("${erp.ai.base-url:http://localhost:11434}") String baseUrl,
			@Value("${erp.ai.model:qwen3:4b}") String model,
			@Value("${erp.ai.timeout-seconds:60}") long timeoutSeconds) {
		this.jsonMapper = jsonMapper;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
		this.generateUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/generate");
		this.model = model;
		this.timeout = Duration.ofSeconds(timeoutSeconds);
	}

	public GenerationStats generate(String prompt, Consumer<String> tokenConsumer)
			throws IOException, InterruptedException {
		long started = System.nanoTime();
		long firstTokenMs = -1;
		long evaluatedTokens = 0;
		String requestBody = jsonMapper.writeValueAsString(Map.of(
				"model", model,
				"prompt", prompt,
				"stream", true,
				"options", Map.of("temperature", 0.2)));
		HttpRequest request = HttpRequest.newBuilder(generateUri)
				.timeout(timeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("Local LLM returned HTTP " + response.statusCode());
		}

		try (Stream<String> lines = response.body()) {
			Iterator<String> iterator = lines.iterator();
			while (iterator.hasNext()) {
				String line = iterator.next();
				JsonNode event = jsonMapper.readTree(line);
				JsonNode error = event.get("error");
				if (error != null) throw new IOException(error.asString());
				JsonNode text = event.get("response");
				if (text != null && !text.asString().isEmpty()) {
					if (firstTokenMs < 0) firstTokenMs = elapsedMs(started);
					tokenConsumer.accept(text.asString());
				}
				JsonNode tokenCount = event.get("eval_count");
				if (tokenCount != null) evaluatedTokens = tokenCount.asLong();
			}
		}
		return new GenerationStats(model, firstTokenMs, elapsedMs(started), evaluatedTokens);
	}

	private static long elapsedMs(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}

	public record GenerationStats(String model, long firstTokenMs, long durationMs,
			long evaluatedTokens) {}
}
