package com.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.erp.service.LocalLlmClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

class LocalLlmClientTest {

	private HttpServer server;
	private ExecutorService executor;
	private LocalLlmClient client;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		executor = Executors.newVirtualThreadPerTaskExecutor();
		server.setExecutor(executor);
		server.start();
		client = new LocalLlmClient(JsonMapper.builder().build(),
				"http://127.0.0.1:" + server.getAddress().getPort(), "test-model", false, 1);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
		executor.shutdownNow();
	}

	@Test
	void streamsTokensBeforeCompletionAndRequiresDone() throws Exception {
		CountDownLatch tokenReceived = new CountDownLatch(1);
		server.createContext("/api/generate", exchange -> {
			try (exchange) {
				assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
						.contains("\"stream\":true", "\"think\":false", "test-model");
				exchange.sendResponseHeaders(200, 0);
				write(exchange, "{\"response\":\"근거 답변\",\"done\":false}\n");
				try {
					assertThat(tokenReceived.await(2, TimeUnit.SECONDS)).isTrue();
				} catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
				write(exchange, "{\"response\":\"\",\"done\":true,\"eval_count\":3}\n");
			}
		});
		StringBuilder answer = new StringBuilder();
		var stats = client.generate("질문", token -> { answer.append(token); tokenReceived.countDown(); });
		assertThat(answer.toString()).isEqualTo("근거 답변");
		assertThat(stats.model()).isEqualTo("test-model");
		assertThat(stats.firstTokenMs()).isBetween(0L, stats.durationMs());
		assertThat(stats.evaluatedTokens()).isEqualTo(3);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"response\":\"답변\",\"done\":false}\n",
			"{\"response\":\"\",\"done\":true}\n",
			"{\"response\":\"   \",\"done\":true}\n",
			"{\"thinking\":\"생각만 있음\",\"done\":true}\n",
			"{\"error\":\"model failed\"}\n",
			"{\"response\":\"답변\",\"done\":\"true\"}\n",
			"{\"response\":\"답변\",\"done\":true}\n{\"error\":\"late error\"}\n",
			"{\"response\":\"답변\",\"done\":true}\ninvalid-json\n"
	})
	void doesNotReportInvalidOrIncompleteStreamsAsSuccess(String body) {
		respond(200, body);
		assertThatThrownBy(() -> client.generate("질문", token -> {})).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsHttpFailureWithoutPublishingErrorBody() {
		respond(503, "{\"response\":\"내부 오류 정보\",\"done\":true}\n");
		StringBuilder answer = new StringBuilder();
		assertThatThrownBy(() -> client.generate("질문", answer::append))
				.isInstanceOf(IOException.class).hasMessageContaining("HTTP 503");
		assertThat(answer).isEmpty();
	}

	@Test
	void totalDeadlineAlsoStopsAnOpenBodyAfterHeadersAndToken() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		stalledBody(release);
		long started = System.nanoTime();
		try {
			assertThatThrownBy(() -> client.generate("질문", token -> {}))
					.isInstanceOf(HttpTimeoutException.class);
			assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(2500);
		} finally { release.countDown(); }
	}

	@Test
	void interruptionCancelsAnOpenResponse() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch tokenReceived = new CountDownLatch(1);
		stalledBody(release);
		AtomicReference<Throwable> result = new AtomicReference<>();
		Thread worker = Thread.ofVirtual().start(() -> {
			try { client.generate("질문", token -> tokenReceived.countDown()); }
			catch (Throwable exception) { result.set(exception); }
		});
		try {
			assertThat(tokenReceived.await(2, TimeUnit.SECONDS)).isTrue();
			worker.interrupt();
			worker.join(2000);
			assertThat(worker.isAlive()).isFalse();
			assertThat(result.get()).isInstanceOf(InterruptedException.class);
		} finally { release.countDown(); worker.interrupt(); }
	}

	private void stalledBody(CountDownLatch release) {
		server.createContext("/api/generate", exchange -> {
			try (exchange) {
				exchange.getRequestBody().readAllBytes();
				exchange.sendResponseHeaders(200, 0);
				write(exchange, "{\"response\":\"답변 일부\",\"done\":false}\n");
				try { release.await(5, TimeUnit.SECONDS); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
			}
		});
	}

	private void respond(int status, String body) {
		server.createContext("/api/generate", exchange -> {
			try (exchange) {
				exchange.getRequestBody().readAllBytes();
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
		});
	}

	private static void write(HttpExchange exchange, String line) throws IOException {
		exchange.getResponseBody().write(line.getBytes(StandardCharsets.UTF_8));
		exchange.getResponseBody().flush();
	}
}
