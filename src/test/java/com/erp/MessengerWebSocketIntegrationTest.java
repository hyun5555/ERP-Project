package com.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import com.erp.service.ChatService;
import com.erp.service.UserService;
import com.erp.mapper.LoginMapper;
import com.erp.vo.ChatMessageVO;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessengerWebSocketIntegrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
			.withDatabaseName("erp")
			.withUsername("erp_test")
			.withPassword("erp_test")
			.withCopyFileToContainer(MountableFile.forHostPath("document/ERP.sql"),
					"/docker-entrypoint-initdb.d/01-erp.sql");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private ChatService chatService;
	@Autowired
	private SimpUserRegistry users;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UserService userService;
	@Autowired
	private LoginMapper loginMapper;
	@Autowired
	private PlatformTransactionManager transactions;

	@Test
	void authenticatedUsersExchangeMessagesReadReceiptsAndRecoverHistoryAfterReconnect() throws Exception {
		try (Peer sender = login("admin"); Peer recipient = login("2005004")) {
			long room = json.readTree(sender.request("POST", "/api/chat/rooms",
					"{\"partnerUsernum\":\"2005004\"}").body()).get("roomNo").asLong();
			sender.connect(sender.csrfHeaders()).get(5, TimeUnit.SECONDS);
			recipient.connect(recipient.csrfHeaders()).get(5, TimeUnit.SECONDS);
			assertThat(sender.serverHeartbeat).containsExactly(10000, 10000);
			sender.subscribe("/user/queue/messages");
			sender.subscribe("/user/queue/read");
			recipient.subscribe("/user/queue/messages");
			sender.ready();
			recipient.ready();

			String content = "인증 사용자 메시지 " + System.nanoTime();
			// Body and STOMP login headers cannot replace the HTTP session's Principal.
			sender.send("/app/chat.send", Map.of("roomNo", room, "content", content,
					"senderUsernum", "2005003"));
			JsonNode sent = sender.nextMessage();
			JsonNode received = recipient.nextMessage();
			assertThat(sent).isEqualTo(received);
			assertThat(received.get("senderUsernum").asString()).isEqualTo("admin");
			assertThat(received.get("content").asString()).isEqualTo(content);
			assertThat(chatService.getMessages(room, "admin")).filteredOn(m -> content.equals(m.getContent()))
					.extracting(ChatMessageVO::getMessageNo).containsExactly(sent.get("messageNo").asLong());

			assertThat(recipient.request("POST", "/api/chat/rooms/" + room + "/read", "{}").statusCode())
					.isEqualTo(200);
			assertThat(sender.nextMessage().get("readerUsernum").asString()).isEqualTo("2005004");

			// Kill the transport, save a message while disconnected, then recover from the durable DB.
			recipient.transport.close();
			await().atMost(Duration.ofSeconds(5)).until(() -> users.getUser("2005004") == null);
			String offline = "연결 중단 중 저장 " + System.nanoTime();
			sender.send("/app/chat.send", Map.of("roomNo", room, "content", offline));
			long offlineId = sender.nextMessage().get("messageNo").asLong();
			recipient.connect(recipient.csrfHeaders()).get(5, TimeUnit.SECONDS);
			recipient.subscribe("/user/queue/messages");
			recipient.ready();
			JsonNode history = json.readTree(recipient.request("GET", "/api/chat/rooms/" + room + "/messages", null).body());
			assertThat(history.findValuesAsString("content")).contains(content, offline);
			assertThat(history.valueStream().filter(message -> message.get("messageNo").asLong() == offlineId).count())
					.isEqualTo(1);

			recipient.send("/app/chat.send", Map.of("roomNo", room, "content", "일반 사원 답장"));
			assertThat(sender.nextMessage().get("senderUsernum").asString()).isEqualTo("2005004");
			assertThat(recipient.nextMessage().get("senderUsernum").asString()).isEqualTo("2005004");
		}
	}

	@Test
	void nonParticipantSendReturnsPrivateErrorWithoutPersisting() throws Exception {
		long room = chatService.openRoom("admin", "2005004");
		int count = chatService.getMessages(room, "admin").size();
		try (Peer stranger = login("2005003")) {
			stranger.connect(stranger.csrfHeaders()).get(5, TimeUnit.SECONDS);
			stranger.subscribe("/user/queue/errors");
			stranger.ready();
			stranger.send("/app/chat.send", Map.of("roomNo", room, "content", "접근 불가"));
			assertThat(stranger.nextMessage().get("message").asString()).isNotBlank();
			assertThat(chatService.getMessages(room, "admin")).hasSize(count);
		}
	}

	@Test
	void simultaneousParticipantsSendWithoutDeadlockOrDuplicatePersistence() throws Exception {
		long room = chatService.openRoom("admin", "2005004");
		try (Peer first = login("admin"); Peer second = login("2005004");
				var executor = Executors.newFixedThreadPool(2)) {
			first.connect(first.csrfHeaders()).get(5, TimeUnit.SECONDS);
			second.connect(second.csrfHeaders()).get(5, TimeUnit.SECONDS);
			first.subscribe("/user/queue/messages");
			second.subscribe("/user/queue/messages");
			first.ready();
			second.ready();
			for (int round = 0; round < 5; round++) {
				String a = "동시 관리자 " + System.nanoTime();
				String b = "동시 사원 " + System.nanoTime();
				CountDownLatch start = new CountDownLatch(1);
				var sendA = executor.submit(() -> { start.await(); first.send("/app/chat.send", Map.of("roomNo", room, "content", a)); return null; });
				var sendB = executor.submit(() -> { start.await(); second.send("/app/chat.send", Map.of("roomNo", room, "content", b)); return null; });
				start.countDown();
				sendA.get(5, TimeUnit.SECONDS);
				sendB.get(5, TimeUnit.SECONDS);
				List<JsonNode> firstReceived = List.of(first.nextMessage(), first.nextMessage());
				List<JsonNode> secondReceived = List.of(second.nextMessage(), second.nextMessage());
				assertThat(firstReceived).extracting(m -> m.get("content").asString()).containsExactlyInAnyOrder(a, b);
				assertThat(secondReceived).containsExactlyInAnyOrderElementsOf(firstReceived);
				assertThat(chatService.getMessages(room, "admin"))
						.filteredOn(m -> a.equals(m.getContent()) || b.equals(m.getContent()))
						.extracting(ChatMessageVO::getContent).containsExactlyInAnyOrder(a, b);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"/queue/messages", "/queue/**", "/topic/unknown", "/app/chat.send",
			"/user/2005004/queue/messages", "/user/queue/unknown"})
	void forbiddenSubscriptionsReturnStompError(String destination) throws Exception {
		try (Peer peer = login("admin")) {
			peer.connect(peer.csrfHeaders()).get(5, TimeUnit.SECONDS);
			peer.session.subscribe(destination, peer.messages);
			assertThat(peer.error.get(5, TimeUnit.SECONDS)).isNotEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"/queue/messages", "/user/queue/messages", "/app/unknown"})
	void directBrokerAndUnknownApplicationPublishesReturnStompError(String destination) throws Exception {
		try (Peer peer = login("admin")) {
			peer.connect(peer.csrfHeaders()).get(5, TimeUnit.SECONDS);
			peer.send(destination, Map.of("content", "위조 메시지"));
			assertThat(peer.error.get(5, TimeUnit.SECONDS)).isNotEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "invalid"})
	void connectRequiresCsrfFromAuthenticatedHttpSession(String kind) throws Exception {
		try (Peer peer = login("admin")) {
			StompHeaders headers = new StompHeaders();
			if (kind.equals("invalid")) headers.add(peer.csrf.get("headerName").asString(), "invalid-token");
			peer.connect(headers);
			assertThat(peer.error.get(5, TimeUnit.SECONDS)).isNotEmpty();
		}
	}

	@Test
	void anonymousWebSocketHandshakeIsRejected() throws Exception {
		try (Peer peer = new Peer("anonymous")) {
			assertThatThrownBy(() -> peer.connect(new StompHeaders()).get(5, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "invalid", "absent"})
	void rejectedOrAbsentConnectCannotPublishFollowingFramesInSameWebSocketMessage(String kind) throws Exception {
		long room = chatService.openRoom("admin", "2005004");
		int count = chatService.getMessages(room, "admin").size();
		try (Peer attacker = login("admin")) {
			CompletableFuture<String> errorFrame = new CompletableFuture<>();
			CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
			WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
			attacker.cookies.get(uri("/ws"), Map.of()).forEach(handshake::put);
			WebSocketSession socket = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
				@Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
					if (message.getPayload().startsWith("ERROR\n")) errorFrame.complete(message.getPayload());
				}
				@Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
					closed.complete(status);
				}
			}, handshake, URI.create("ws://localhost:" + port + "/ERP/ws")).get(5, TimeUnit.SECONDS);
			try {
				String connect = kind.equals("absent") ? "" : "CONNECT\naccept-version:1.2\nheart-beat:0,0\n"
						+ (kind.equals("invalid") ? attacker.csrf.get("headerName").asString() + ":invalid-token\n" : "") + "\n\0";
				String send = "SEND\ndestination:/app/chat.send\ncontent-type:application/json\n\n"
						+ json.writeValueAsString(Map.of("roomNo", room, "content", "CONNECT 경계 공격")) + "\0";
				String subscribe = "SUBSCRIBE\nid:attack\ndestination:/user/queue/messages\n\n\0";
				socket.sendMessage(new TextMessage(connect + send + subscribe));
				assertThat(errorFrame.get(5, TimeUnit.SECONDS)).startsWith("ERROR\n");
				assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.PROTOCOL_ERROR.getCode());
				assertThat(chatService.getMessages(room, "admin")).hasSize(count);
			} finally {
				if (socket.isOpen()) socket.close();
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"demotion", "retirement", "deletion"})
	void committedAccountChangesCloseAllExistingSocketsAndPreserveOtherUsers(String change) throws Exception {
		String target = insertEmployee();
		try (Peer first = login(target); Peer second = login(target.toLowerCase(Locale.ROOT)); Peer admin = login("admin")) {
			first.connect(first.csrfHeaders()).get(5, TimeUnit.SECONDS);
			second.connect(second.csrfHeaders()).get(5, TimeUnit.SECONDS);
			admin.connect(admin.csrfHeaders()).get(5, TimeUnit.SECONDS);
			first.ready();
			second.ready();
			admin.subscribe("/user/queue/messages");
			admin.ready();
			HttpResponse<String> changed = change.equals("deletion")
					? admin.request("DELETE", "/api/users/" + target, "{}")
					: admin.request("PUT", "/api/users/" + target, json.writeValueAsString(Map.ofEntries(
							Map.entry("userpw", ""), Map.entry("name", "회수 검증"), Map.entry("idnum1", "990101"),
							Map.entry("idnum2", "1"), Map.entry("phonenum", "010-1111-2222"),
							Map.entry("officenum", "02-111-2222"), Map.entry("email", "socket@example.com"),
							Map.entry("team", "개발"), Map.entry("level", "사원"), Map.entry("joindate", "2026-10-21"),
							Map.entry("authority", change.equals("retirement")),
							Map.entry("userStatus", change.equals("retirement") ? "퇴직" : "재직"))));
			assertThat(changed.statusCode()).isEqualTo(200);
			assertThat(first.closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
			assertThat(second.closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
			assertThat(json.readTree(first.request("GET", "/api/session", null).body()).get("authenticated").asBoolean()).isFalse();
			assertThat(json.readTree(second.request("GET", "/api/session", null).body()).get("authenticated").asBoolean()).isFalse();
			await().atMost(Duration.ofSeconds(5)).until(() -> users.getUser(target) == null);
			assertThat(admin.transport.isOpen()).isTrue();
			admin.ready();
			long room = chatService.openRoom("admin", "2005004");
			String content = "무관 세션은 계속 전송 " + UUID.randomUUID();
			admin.send("/app/chat.send", Map.of("roomNo", room, "content", content));
			assertThat(admin.nextMessage().get("content").asString()).isEqualTo(content);
			assertThat(chatService.getMessages(room, "2005004")).extracting(ChatMessageVO::getContent).contains(content);
		} finally {
			jdbc.update("delete from user where usernum = ?", target);
		}
	}

	@Test
	void rolledBackAccountChangeLeavesExistingSocketUsable() throws Exception {
		String target = insertEmployee();
		try (Peer peer = login(target)) {
			peer.connect(peer.csrfHeaders()).get(5, TimeUnit.SECONDS);
			peer.ready();
			var demoted = userService.getUser(target);
			demoted.setAuthority(false);
			new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
				userService.updateUser(demoted, loginMapper.findByUsernum("admin"));
				transaction.setRollbackOnly();
			});
			assertThat(userService.getUser(target).isAuthority()).isTrue();
			peer.ready();
			assertThat(peer.closed.isDone()).isFalse();
			assertThat(peer.request("GET", "/api/users", null).statusCode()).isEqualTo(200);
		} finally {
			jdbc.update("delete from user where usernum = ?", target);
		}
	}

	@Test
	void canonicalEmployeeNumbersDeliverMessagesAndBoundedReadReceiptsEvenForLegacyRoomAliases() throws Exception {
		String first = insertEmployee();
		String second = insertEmployee();
		try (Peer sender = login(first.toLowerCase(Locale.ROOT)); Peer recipient = login(second)) {
			String body = json.writeValueAsString(Map.of("partnerUsernum", second.toLowerCase(Locale.ROOT)));
			HttpResponse<String> opened = sender.request("POST", "/api/chat/rooms", body);
			assertThat(opened.statusCode()).isEqualTo(200);
			long room = json.readTree(opened.body()).get("roomNo").asLong();
			assertThat(jdbc.queryForMap("select user_a,user_b from chat_room where room_no=?", room).values())
					.containsExactlyInAnyOrder(first, second);
			assertThat(json.readTree(recipient.request("POST", "/api/chat/rooms",
					json.writeValueAsString(Map.of("partnerUsernum", first.toLowerCase(Locale.ROOT)))).body()).get("roomNo").asLong()).isEqualTo(room);
			assertThat(sender.request("POST", "/api/chat/rooms",
					json.writeValueAsString(Map.of("partnerUsernum", first.toLowerCase(Locale.ROOT)))).statusCode()).isEqualTo(400);
			// Older rows can contain case aliases even though the FK matches the canonical user.
			jdbc.update("update chat_room set user_a=lower(user_a),user_b=lower(user_b) where room_no=?", room);
			sender.connect(sender.csrfHeaders()).get(5, TimeUnit.SECONDS);
			recipient.connect(recipient.csrfHeaders()).get(5, TimeUnit.SECONDS);
			sender.subscribe("/user/queue/messages");
			sender.subscribe("/user/queue/read");
			recipient.subscribe("/user/queue/messages");
			sender.ready();
			recipient.ready();
			sender.send("/app/chat.send", Map.of("roomNo", room, "content", "정식 번호로 전달"));
			JsonNode firstMessage = sender.nextMessage();
			assertThat(recipient.nextMessage()).isEqualTo(firstMessage);
			assertThat(firstMessage.get("senderUsernum").asString()).isEqualTo(first);
			sender.send("/app/chat.send", Map.of("roomNo", room, "content", "아직 읽지 않은 후속 메시지"));
			JsonNode secondMessage = sender.nextMessage();
			assertThat(recipient.nextMessage()).isEqualTo(secondMessage);
			long visible = firstMessage.get("messageNo").asLong();
			assertThat(recipient.request("POST", "/api/chat/rooms/" + room + "/read?throughMessageNo=" + visible, "{}").statusCode()).isEqualTo(200);
			JsonNode receipt = sender.nextMessage();
			assertThat(receipt.get("readerUsernum").asString()).isEqualTo(second);
			assertThat(receipt.get("throughMessageNo").asLong()).isEqualTo(visible);
			assertThat(jdbc.queryForObject("select read_at is null from chat_message where message_no=?", Boolean.class,
					secondMessage.get("messageNo").asLong())).isTrue();
		} finally {
			jdbc.update("delete from chat_room where user_a in (?,?) or user_b in (?,?)", first, second, first, second);
			jdbc.update("delete from user where usernum in (?,?)", first, second);
		}
	}

	private String insertEmployee() {
		String usernum = "WS-" + UUID.randomUUID().toString().replace("-", "");
		jdbc.update("insert into user (usernum, userpw, name, idnum1, idnum2, phonenum, officenum, email, "
				+ "team, level, firstlogin, authority, level_num, user_status) "
				+ "values (?, MD5('1234'), '회수 검증', '990101', '1', '010-1111-2222', '02-111-2222', "
				+ "'socket@example.com', '개발', '사원', false, true, 400, '재직')", usernum);
		return usernum;
	}

	private Peer login(String usernum) throws Exception {
		Peer peer = new Peer(usernum);
		peer.csrf = json.readTree(peer.request("GET", "/api/session", null).body()).get("csrf");
		String form = peer.csrf.get("parameterName").asString() + "=" + encode(peer.csrf.get("token").asString())
				+ "&usernum=" + encode(usernum) + "&userpw=1234";
		HttpResponse<String> response = peer.http.send(HttpRequest.newBuilder(uri("/login/login.do"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		peer.csrf = json.readTree(peer.request("GET", "/api/session", null).body()).get("csrf");
		return peer;
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + "/ERP" + path);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private class Peer implements AutoCloseable {
		final String usernum;
		final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
				.connectTimeout(Duration.ofSeconds(5)).build();
		final LinkedBlockingQueue<byte[]> inbox = new LinkedBlockingQueue<>();
		final StompFrameHandler messages = new StompFrameHandler() {
			@Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
			@Override public void handleFrame(StompHeaders headers, Object payload) { inbox.add((byte[]) payload); }
		};
		CompletableFuture<String> error;
		CompletableFuture<CloseStatus> closed;
		final ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
		long[] serverHeartbeat;
		JsonNode csrf;
		WebSocketStompClient client;
		WebSocketSession transport;
		StompSession session;

		Peer(String usernum) { this.usernum = usernum; heartbeat.initialize(); }

		StompHeaders csrfHeaders() {
			StompHeaders headers = new StompHeaders();
			headers.add(csrf.get("headerName").asString(), csrf.get("token").asString());
			headers.setLogin("2005003");
			headers.setPasscode("not-an-http-login");
			return headers;
		}

		CompletableFuture<StompSession> connect(StompHeaders connectHeaders) throws Exception {
			error = new CompletableFuture<>();
			closed = new CompletableFuture<>();
			CompletableFuture<StompSession> connected = new CompletableFuture<>();
			client = new WebSocketStompClient(new StandardWebSocketClient() {
				@Override
				protected CompletableFuture<WebSocketSession> executeInternal(WebSocketHandler handler,
						HttpHeaders headers, URI url, List<String> protocols, List<WebSocketExtension> extensions,
						Map<String, Object> attributes) {
					WebSocketHandler tracked = new WebSocketHandlerDecorator(handler) {
						@Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) throws Exception {
							try { super.afterConnectionClosed(socket, status); }
							finally { closed.complete(status); }
						}
					};
					return super.executeInternal(tracked, headers, url, protocols, extensions, attributes)
							.thenApply(socket -> { transport = socket; return socket; });
				}
			});
			client.setMessageConverter(new ByteArrayMessageConverter() {
				{ addSupportedMimeTypes(MimeTypeUtils.ALL); }
			});
			client.setTaskScheduler(heartbeat);
			client.setDefaultHeartbeat(new long[] {10000, 10000});
			WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
			cookies.get(uri("/ws"), Map.of()).forEach(handshake::put);
			client.connectAsync("ws://localhost:" + port + "/ERP/ws", handshake, connectHeaders,
					new StompSessionHandlerAdapter() {
						@Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
						@Override public void afterConnected(StompSession established, StompHeaders headers) {
							session = established;
							serverHeartbeat = headers.getHeartbeat();
							connected.complete(established);
						}
						@Override public void handleFrame(StompHeaders headers, Object payload) {
							error.complete(headers.getFirst("message")
								+ (payload == null ? "" : new String((byte[]) payload, StandardCharsets.UTF_8)));
						}
						@Override public void handleException(StompSession connected, StompCommand command,
								StompHeaders headers, byte[] payload, Throwable exception) {
							error.completeExceptionally(exception);
						}
						@Override public void handleTransportError(StompSession connected, Throwable exception) {
							error.completeExceptionally(exception);
						}
					}).whenComplete((established, exception) -> {
						if (exception != null) connected.completeExceptionally(exception);
					});
			return connected;
		}

		void subscribe(String destination) {
			session.subscribe(destination, messages);
		}

		void ready() throws Exception {
			CompletableFuture<Boolean> ready = new CompletableFuture<>();
			StompSession.Subscription subscription = session.subscribe("/user/queue/sync", new StompFrameHandler() {
				@Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
				@Override public void handleFrame(StompHeaders headers, Object payload) {
					ready.complete(json.readTree((byte[]) payload).get("ready").asBoolean());
				}
			});
			send("/app/chat.sync", Map.of());
			assertThat(ready.get(5, TimeUnit.SECONDS)).isTrue();
			subscription.unsubscribe();
		}

		void send(String destination, Map<String, ?> body) {
			StompHeaders headers = new StompHeaders();
			headers.setDestination(destination);
			headers.setContentType(MediaType.APPLICATION_JSON);
			session.send(headers, json.writeValueAsBytes(body));
		}

		JsonNode nextMessage() {
			try {
				byte[] payload = inbox.poll(5, TimeUnit.SECONDS);
				assertThat(payload).as("received STOMP message for %s", usernum).isNotNull();
				return json.readTree(payload);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
		}

		HttpResponse<String> request(String method, String path, String body) throws Exception {
			HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5))
					.header("Accept", "application/json");
			if (body == null) request.GET();
			else request.header("Content-Type", "application/json")
					.header(csrf.get("headerName").asString(), csrf.get("token").asString())
					.method(method, HttpRequest.BodyPublishers.ofString(body));
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}

		@Override public void close() throws IOException {
			if (transport != null && transport.isOpen()) transport.close();
			if (client != null) client.stop();
			heartbeat.shutdown();
			http.close();
		}
	}
}
