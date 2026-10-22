package com.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.erp.vo.searchVO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit native-only benchmark, excluded unless the owned performance root is supplied. */
@EnabledIfEnvironmentVariable(named = "ERP_PERF_ROOT", matches = ".+")
class NativePerformanceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "performance-local-only";
    private final AtomicInteger failures = new AtomicInteger();
    private Path root, output;
    private String base;
    private Properties properties;
    private int repeats, samples, messageSamples;

    @Test
    void measureOwnedNativeEnvironment() throws Exception {
        Path project = Path.of("").toRealPath();
        root = Path.of(System.getenv("ERP_PERF_ROOT")).toRealPath();
        assertEquals(project.resolve(".local-dev"), root.getParent());
        assertTrue(root.getFileName().toString().startsWith("performance-"));
        assertTrue(Files.isRegularFile(root.resolve("performance-fixture.json")));
        output = Path.of(System.getenv("ERP_PERF_OUTPUT")).toRealPath();
        assertTrue(output.startsWith(project.resolve("performance/results")));
        properties = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("run/app.properties"))) { properties.load(reader); }
        JsonNode state = JSON.readTree(Files.readString(root.resolve("environment.json")));
        assertEquals(root.toString(), state.get("root").asString());
        assertEquals("127.0.0.1", properties.getProperty("server.address"));
        assertTrue(properties.getProperty("spring.datasource.url").startsWith("jdbc:mysql://127.0.0.1:" + state.get("db_port").asInt() + "/erp?"));
        base = "http://127.0.0.1:" + state.get("app_port").asInt() + "/ERP";
        repeats = Integer.parseInt(System.getenv("ERP_PERF_REPEATS"));
        samples = Integer.parseInt(System.getenv("ERP_PERF_SAMPLES"));
        messageSamples = Integer.parseInt(System.getenv("ERP_PERF_MESSAGES"));
        assertTrue(repeats >= 3 && samples > 0 && messageSamples > 0);
        // Complete legacy password rehashes and connection setup before read-only samples.
        for (String user : List.of("perf-u000", "perf-admin", "perf-u097")) try (Peer ignored = login(user)) { }
        try (Connection db = connect()) {
            db.setReadOnly(true);
            List<ApiCase> cases = apiCases(db);
            sqlPlans(db, cases);
            Files.writeString(output.resolve("java-environment.json"), JSON.writeValueAsString(Map.of("java", System.getProperty("java.runtime.version"), "vm", System.getProperty("java.vm.name"), "mysql", db.getMetaData().getDatabaseProductVersion(), "http_cases", cases.size(), "planned_http_samples", cases.size() * samples * repeats * 6, "http_timeout_seconds", 10, "stomp_deadline_seconds", 5)));
            for (int repeat = 1; repeat <= repeats; repeat++) {
                for (int concurrency : List.of(1, 5)) {
                    try (var workers = Executors.newFixedThreadPool(concurrency)) {
                        List<CompletableFuture<Void>> tasks = new ArrayList<>();
                        CyclicBarrier boundary = new CyclicBarrier(concurrency);
                        for (int worker = 0; worker < concurrency; worker++) {
                            int r = repeat, w = worker;
                            tasks.add(CompletableFuture.runAsync(() -> {
                                try (Peer user = login("perf-u000"); Peer admin = login("perf-admin"); Peer approver = login("perf-u097")) {
                                    for (ApiCase c : cases) {
                                        Peer peer = c.role.equals("admin") ? admin : c.role.equals("approver") ? approver : user;
                                        for (int i = -5; i < 0; i++) measureApi(peer, c, r, concurrency, w, i);
                                        boundary.await(30, TimeUnit.SECONDS);
                                        for (int i = 0; i < samples; i++) measureApi(peer, c, r, concurrency, w, i);
                                        boundary.await(30, TimeUnit.SECONDS);
                                    }
                                } catch (Exception error) { throw new RuntimeException(error); }
                            }, workers));
                        }
                        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(30, TimeUnit.MINUTES);
                    }
                }
            }
        }
        for (int repeat = 1; repeat <= repeats; repeat++) {
            for (int rooms : List.of(1, 5)) {
                int r = repeat;
                try (Connection db = connect()) {
                    db.createStatement().executeUpdate("DELETE FROM chat_message WHERE content LIKE 'PERF-PROBE-%'");
                    try (PreparedStatement statement = db.prepareStatement("DELETE FROM chat_room WHERE user_a=? AND user_b=?")) {
                        for (int pair = 0; pair < 5; pair++) { statement.setString(1, String.format("perf-u%03d", 80 + pair * 2)); statement.setString(2, String.format("perf-u%03d", 81 + pair * 2)); statement.executeUpdate(); }
                    }
                    long actualRooms = scalar(db, "SELECT COUNT(*) FROM chat_room"), actualMessages = scalar(db, "SELECT COUNT(*) FROM chat_message");
                    long expectedMessages = JSON.readTree(Files.readString(root.resolve("performance-fixture.json"))).get("size").asLong();
                    boolean same = actualRooms == 100 && actualMessages == expectedMessages;
                    append("messages", Map.of("case", "baseline", "repeat", repeat, "concurrency", rooms, "ok", same, "elapsed_ms", 0, "rooms", actualRooms, "messages", actualMessages));
                    if (!same) failures.incrementAndGet();
                }
                try (var workers = Executors.newFixedThreadPool(rooms)) {
                    List<CompletableFuture<Void>> tasks = new ArrayList<>();
                    for (int pair = 0; pair < rooms; pair++) {
                        int p = pair;
                        tasks.add(CompletableFuture.runAsync(() -> {
                            try { messageRound(r, rooms, p); } catch (Exception error) { throw new RuntimeException(error); }
                        }, workers));
                    }
                    CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.MINUTES);
                }
            }
        }
        assertEquals(0, failures.get(), "Inspect per-attempt raw records; failures remain in the denominator");
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(properties.getProperty("spring.datasource.url"), properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"));
    }

    private record ApiCase(String name, String role, String path, String mapper, Object parameter, long count, String id, List<String> ids) {}

    private List<ApiCase> apiCases(Connection db) throws Exception {
        List<ApiCase> result = new ArrayList<>();
        int size = JSON.readTree(Files.readString(root.resolve("performance-fixture.json"))).get("size").asInt();
        for (int page : List.of(1, Math.min(1000, size / 10))) {
            searchVO search = new searchVO(); search.setPageno(page); search.setSearchWord("PERF");
            String where = "notice_title LIKE '%PERF%'";
            result.add(new ApiCase("notices-page-" + page, "user", "/api/notices?searchWord=PERF&page=" + page, "NoticeMapper.selectNotices", search, size, "noticeNo", values(db, "SELECT notice_no FROM notice WHERE " + where + " ORDER BY is_important DESC,notice_no DESC LIMIT " + (page - 1) * 10 + ",10")));
        }
        for (String keyword : List.of("rare", "absent")) {
            searchVO search = new searchVO(); search.setPageno(1); search.setSearchWord(keyword);
            result.add(new ApiCase("notices-search-" + keyword, "user", "/api/notices?searchWord=" + keyword, "NoticeMapper.selectNotices", search, scalar(db, "SELECT COUNT(*) FROM notice WHERE notice_title LIKE '%" + keyword + "%'"), "noticeNo", values(db, "SELECT notice_no FROM notice WHERE notice_title LIKE '%" + keyword + "%' ORDER BY is_important DESC,notice_no DESC LIMIT 10")));
        }
        Map<String, Object> mine = new HashMap<>(Map.of("usernum", "perf-u000", "offset", 0, "limit", 10, "kind", "", "status", "", "keyword", "PERF"));
        List<String> expectedCounts = new ArrayList<>();
        for (String status : List.of("대기중", "반려", "진행중", "승인")) expectedCounts.add(status + "=" + scalar(db, "SELECT COUNT(*) FROM approval WHERE usernum='perf-u000' AND document_status='" + status + "'"));
        expectedCounts.add("수신=0");
        result.add(new ApiCase("dashboard", "user", "/api/dashboard", "ApprovalMapper.countAppList", mine, 5, "counts", expectedCounts));
        result.add(new ApiCase("drafts", "user", "/api/approvals?keyword=PERF", "ApprovalMapper.selectAppList", mine, scalar(db, "SELECT COUNT(*) FROM approval WHERE usernum='perf-u000' AND approval_title LIKE '%PERF%'"), "approvalNo", values(db, "SELECT approval_no FROM approval WHERE usernum='perf-u000' AND approval_title LIKE '%PERF%' ORDER BY approval_no DESC LIMIT 10")));
        Map<String, Object> received = new HashMap<>(mine); received.put("usernum", "perf-u097");
        result.add(new ApiCase("received", "approver", "/api/approvals/received?keyword=PERF", "ApprovalMapper.selectRecvApprovalList", received, size, "approvalNo", values(db, "SELECT approval_no FROM approval WHERE approval_title LIKE '%PERF%' ORDER BY approval_no DESC LIMIT 10")));
        Map<String, Object> completed = new HashMap<>(mine); completed.put("team", "개발");
        String completedWhere = "a.document_status='승인' AND u.team='개발' AND a.approval_title LIKE '%PERF%'";
        result.add(new ApiCase("completed-development", "user", "/api/approvals/completed?keyword=PERF&team=" + encode("개발"), "ApprovalMapper.selectListAll", completed, scalar(db, "SELECT COUNT(*) FROM approval a JOIN user u ON u.usernum=a.usernum WHERE " + completedWhere), "approvalNo", values(db, "SELECT a.approval_no FROM approval a JOIN user u ON u.usernum=a.usernum WHERE " + completedWhere + " ORDER BY a.approval_no DESC LIMIT 10")));
        searchVO people = new searchVO(); people.setPageno(1); people.setSearchKey("usernum"); people.setSearchWord("perf-");
        result.add(new ApiCase("users", "admin", "/api/users?searchKey=usernum&searchWord=perf-", "UserMapper.selectUsers", people, 101, "usernum", values(db, "SELECT usernum FROM user WHERE usernum LIKE '%perf-%' ORDER BY FIELD(team,'임원','개발','경영지원','디자인'),level_num,usernum LIMIT 10")));
        result.add(new ApiCase("rooms", "user", "/api/chat/rooms", "ChatMapper.selectRooms", Map.of("usernum", "perf-u000"), 99, "roomNo", values(db, "SELECT r.room_no FROM chat_room r JOIN user u ON u.usernum=CASE WHEN r.user_a='perf-u000' THEN r.user_b ELSE r.user_a END WHERE 'perf-u000' IN (r.user_a,r.user_b) ORDER BY COALESCE(r.last_message_at,r.created_at) DESC,r.room_no DESC")));
        result.add(new ApiCase("history", "user", "/api/chat/rooms/1000000/messages", "ChatMapper.selectMessages", Map.of("roomNo", 1000000L), scalar(db, "SELECT COUNT(*) FROM chat_message WHERE room_no=1000000"), "messageNo", values(db, "SELECT message_no FROM chat_message WHERE room_no=1000000 ORDER BY message_no")));
        // Independent SQL verifies every result identifier; this is not a 200-only latency probe.
        return result;
    }

    private void measureApi(Peer peer, ApiCase c, int repeat, int concurrency, int worker, int sequence) {
        long start = System.nanoTime();
        Map<String, Object> row = new LinkedHashMap<>(Map.of("repeat", repeat, "case", c.name, "concurrency", concurrency, "worker", worker, "sequence", sequence, "warmup", sequence < 0, "started_ns", start));
        boolean ok = false;
        try {
            HttpResponse<byte[]> response = peer.request("GET", c.path, null);
            row.put("status", response.statusCode()); row.put("bytes", response.body().length);
            JsonNode value = JSON.readTree(response.body());
            List<String> ids = new ArrayList<>();
            long actual;
            if (c.id.equals("counts")) {
                for (String key : List.of("대기중", "반려", "진행중", "승인", "수신")) ids.add(key + "=" + value.get("counts").get(key).asLong());
                actual = value.get("counts").size();
            } else {
                JsonNode items = value.isArray() ? value : value.get("items");
                if (items != null) for (JsonNode item : items) ids.add(item.get(c.id).isTextual() ? item.get(c.id).asString() : String.valueOf(item.get(c.id).asLong()));
                actual = value.isArray() ? value.size() : value.get("totalCount").asLong();
            }
            ok = response.statusCode() == 200 && actual == c.count && (c.ids.isEmpty() ? ids.size() == c.count : ids.equals(c.ids));
            row.put("expected_count", c.count); row.put("actual_count", actual); row.put("result_ids", ids);
            if (!ok) row.put("error", "status, count or ordered identifiers differ from fixture SQL");
        } catch (Exception error) { row.put("error", error.getClass().getSimpleName()); row.put("timeout", error instanceof java.net.http.HttpTimeoutException); }
        row.put("ok", ok); row.put("completed_ns", System.nanoTime()); row.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0);
        if (!ok) failures.incrementAndGet();
        append("http", row);
    }

    private void sqlPlans(Connection db, List<ApiCase> cases) throws Exception {
        Configuration configuration = new Configuration();
        try (var paths = Files.list(Path.of("src/main/resources/mapper"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                try (var in = Files.newInputStream(path)) { new XMLMapperBuilder(in, configuration, path.toString(), configuration.getSqlFragments()).parse(); }
            }
        }
        Path directory = output.resolve("sql"); Files.createDirectory(directory);
        List<ApiCase> sqlCases = new ArrayList<>(cases.stream().filter(c -> !c.id.equals("counts")).toList());
        for (String status : List.of("대기중", "반려", "진행중", "승인", "수신")) {
            Map<String, Object> parameter = new HashMap<>(Map.of("usernum", "perf-u000", "kind", "", "keyword", "", "offset", 0, "limit", 1, "status", status.equals("수신") ? "" : status));
            sqlCases.add(new ApiCase("dashboard-" + status, "user", "/api/dashboard", status.equals("수신") ? "ApprovalMapper.countRecvApprovalList" : "ApprovalMapper.countAppList", parameter, 0, "", List.of()));
        }
        sqlCases.add(new ApiCase("dashboard-notices", "user", "/api/dashboard", "NoticeMapper.selectMainNotices", Map.of(), 0, "", List.of()));
        for (ApiCase c : sqlCases) {
          List<String> statements = new ArrayList<>(List.of(c.mapper));
          String count = switch (c.mapper) {
            case "NoticeMapper.selectNotices" -> "NoticeMapper.countNotices";
            case "UserMapper.selectUsers" -> "UserMapper.countUsers";
            case "ApprovalMapper.selectAppList" -> "ApprovalMapper.countAppList";
            case "ApprovalMapper.selectRecvApprovalList" -> "ApprovalMapper.countRecvApprovalList";
            case "ApprovalMapper.selectListAll" -> "ApprovalMapper.selectListAllCount";
            default -> null;
          };
          if (count != null) statements.add(count);
          for (String statementId : statements) {
            MappedStatement mapped = configuration.getMappedStatement("com.erp.mapper." + statementId);
            BoundSql bound = mapped.getBoundSql(c.parameter);
            List<Object> parameters = new ArrayList<>();
            for (var parameter : bound.getParameterMappings()) parameters.add(bound.hasAdditionalParameter(parameter.getProperty()) ? bound.getAdditionalParameter(parameter.getProperty()) : configuration.newMetaObject(c.parameter).getValue(parameter.getProperty()));
            Map<String, Object> record = new LinkedHashMap<>(Map.of("statement", mapped.getId(), "sql", bound.getSql(), "parameters", parameters, "api", c.path));
            assertTrue(bound.getSql().stripLeading().toUpperCase().startsWith("SELECT"));
            record.put("explain_json", explain(db, "EXPLAIN FORMAT=JSON ", bound, parameters));
            List<Map<String, Object>> analyze = new ArrayList<>();
            for (int repeat = 1; repeat <= repeats; repeat++) analyze.add(Map.of("repeat", repeat, "tree", explain(db, "EXPLAIN ANALYZE FORMAT=TREE ", bound, parameters)));
            record.put("analyze_runs", analyze);
            Files.writeString(directory.resolve(c.name + "-" + statementId.substring(statementId.indexOf('.') + 1) + ".json"), JSON.writeValueAsString(record));
          }
        }
        for (String table : List.of("user", "notice", "notice_team", "approval", "approval_line", "chat_room", "chat_message")) {
            Files.writeString(directory.resolve(table + "-schema.txt"), String.join("\n", values(db, "SHOW CREATE TABLE `" + table + "`", 2)) + "\nINDEXES\n" + String.join("\n", rows(db, "SHOW INDEX FROM `" + table + "`")));
        }
    }

    private List<String> explain(Connection db, String prefix, BoundSql bound, List<Object> parameters) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(prefix + bound.getSql())) {
            statement.setQueryTimeout(30);
            for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
            try (ResultSet result = statement.executeQuery()) { List<String> values = new ArrayList<>(); while (result.next()) values.add(result.getString(1)); return values; }
        }
    }

    private void messageRound(int repeat, int concurrency, int pair) throws Exception {
        String first = String.format("perf-u%03d", 80 + pair * 2), second = String.format("perf-u%03d", 81 + pair * 2);
        String prefix = "PERF-PROBE-" + repeat + "-" + concurrency + "-" + pair + "-";
        try (Connection db = connect(); Peer a = login(first); Peer b = login(second)) {
            // Reset only this tool's own live-message samples, never the seeded history or another root.
            try (PreparedStatement cleanup = db.prepareStatement("DELETE FROM chat_message WHERE content LIKE 'PERF-PROBE-%' AND sender_usernum IN (?,?)")) { cleanup.setString(1, first); cleanup.setString(2, second); cleanup.executeUpdate(); }
            long room = JSON.readTree(a.request("POST", "/api/chat/rooms", JSON.writeValueAsBytes(Map.of("partnerUsernum", second))).body()).get("roomNo").asLong();
            a.connect(); b.connect();
            for (int sequence = -5; sequence < messageSamples; sequence++) {
                Peer sender = sequence % 2 == 0 ? a : b, recipient = sender == a ? b : a;
                String content = prefix + sequence;
                long start = System.nanoTime(); boolean ok = false;
                Map<String, Object> row = new LinkedHashMap<>(Map.of("case", "recipient-message", "repeat", repeat, "concurrency", concurrency, "pair", pair, "sequence", sequence, "warmup", sequence < 0));
                try {
                    sender.send("/app/chat.send", Map.of("roomNo", room, "content", content));
                    Envelope received = recipient.inbox.poll(5, TimeUnit.SECONDS);
                    Envelope echo = sender.inbox.poll(5, TimeUnit.SECONDS);
                    if (received != null) {
                        row.put("elapsed_ms", (received.nanoTime - start) / 1_000_000.0);
                        row.put("message_no", received.body.get("messageNo").asLong());
                        row.put("echo_ms", echo == null ? null : (echo.nanoTime - start) / 1_000_000.0);
                        ok = received.body.get("content").asString().equals(content) && received.body.get("senderUsernum").asString().equals(sender.user) && echo != null && echo.body.equals(received.body);
                    } else row.put("timeout", true);
                } catch (Exception error) { row.put("error", error.getClass().getSimpleName()); }
                row.putIfAbsent("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0); row.put("ok", ok);
                if (!ok && sequence >= 0) failures.incrementAndGet();
                append("messages", row);
            }
            long stored = scalar(db, "SELECT COUNT(*) FROM chat_message WHERE content LIKE '" + prefix + "%'"), extras = a.inbox.size() + b.inbox.size();
            boolean intact = stored == messageSamples + 5 && extras == 0;
            append("messages", Map.of("case", "integrity", "repeat", repeat, "concurrency", concurrency, "pair", pair, "ok", intact, "elapsed_ms", 0, "planned_stored_with_warmup", messageSamples + 5, "actual_stored", stored, "extra_callbacks", extras));
            if (!intact) failures.incrementAndGet();
            b.disconnect();
            List<Long> offline = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                a.send("/app/chat.send", Map.of("roomNo", room, "content", prefix + "offline-" + i));
                Envelope echo = a.inbox.poll(5, TimeUnit.SECONDS); assertTrue(echo != null); offline.add(echo.body.get("messageNo").asLong());
            }
            long reconnect = System.nanoTime(); b.connect(); long connected = System.nanoTime();
            JsonNode history = JSON.readTree(b.request("GET", "/api/chat/rooms/" + room + "/messages", null).body());
            List<Long> restored = new ArrayList<>(); for (JsonNode message : history) if (offline.contains(message.get("messageNo").asLong())) restored.add(message.get("messageNo").asLong());
            boolean recovered = restored.equals(offline);
            append("messages", Map.of("case", "reconnect-history", "repeat", repeat, "concurrency", concurrency, "pair", pair, "ok", recovered, "elapsed_ms", (System.nanoTime() - reconnect) / 1_000_000.0, "connection_sync_ms", (connected - reconnect) / 1_000_000.0, "expected_ids", offline, "restored_ids", restored));
            if (!recovered) failures.incrementAndGet();
        }
    }

    private long scalar(Connection db, String sql) throws Exception { return Long.parseLong(values(db, sql).getFirst()); }
    private List<String> values(Connection db, String sql) throws Exception { return values(db, sql, 1); }
    private List<String> values(Connection db, String sql, int column) throws Exception { try (var statement = db.createStatement()) { statement.setQueryTimeout(30); try (var result = statement.executeQuery(sql)) { List<String> values = new ArrayList<>(); while (result.next()) values.add(result.getString(column)); return values; } } }
    private List<String> rows(Connection db, String sql) throws Exception { try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) { List<String> rows = new ArrayList<>(); while (result.next()) { List<String> row = new ArrayList<>(); for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.add(result.getString(i)); rows.add(row.toString()); } return rows; } }
    private synchronized void append(String name, Map<String, ?> row) { try { Files.writeString(output.resolve(name + ".jsonl"), JSON.writeValueAsString(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception error) { throw new RuntimeException(error); } }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record Envelope(JsonNode body, long nanoTime) {}

    private Peer login(String user) throws Exception {
        Peer peer = new Peer(user);
        peer.csrf = JSON.readTree(peer.request("GET", "/api/session", null).body()).get("csrf");
        String form = "usernum=" + user + "&userpw=" + PASSWORD + "&" + peer.csrf.get("parameterName").asString() + "=" + encode(peer.csrf.get("token").asString());
        var response = peer.http.send(HttpRequest.newBuilder(URI.create(base + "/login/login.do")).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); assertEquals("OK", response.body());
        peer.csrf = JSON.readTree(peer.request("GET", "/api/session", null).body()).get("csrf");
        return peer;
    }

    private class Peer implements AutoCloseable {
        final String user;
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        final LinkedBlockingQueue<Envelope> inbox = new LinkedBlockingQueue<>();
        JsonNode csrf;
        WebSocketStompClient socket;
        StompSession session;
        ThreadPoolTaskScheduler scheduler;
        Peer(String user) { this.user = user; }
        HttpResponse<byte[]> request(String method, String path, byte[] body) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
            if (body == null) request.GET(); else request.header("Content-Type", "application/json").header(csrf.get("headerName").asString(), csrf.get("token").asString()).method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        void connect() throws Exception {
            inbox.clear(); scheduler = new ThreadPoolTaskScheduler(); scheduler.initialize();
            socket = new WebSocketStompClient(new StandardWebSocketClient());
            socket.setMessageConverter(new ByteArrayMessageConverter() {{ addSupportedMimeTypes(MimeTypeUtils.ALL); }});
            socket.setTaskScheduler(scheduler); socket.setDefaultHeartbeat(new long[] {10000, 10000});
            WebSocketHttpHeaders handshake = new WebSocketHttpHeaders(); cookies.get(URI.create(base + "/ws"), Map.of()).forEach(handshake::put);
            StompHeaders headers = new StompHeaders(); headers.add(csrf.get("headerName").asString(), csrf.get("token").asString());
            session = socket.connectAsync(base.replace("http:", "ws:") + "/ws", handshake, headers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
            session.subscribe("/user/queue/messages", new StompFrameHandler() {
                public Type getPayloadType(StompHeaders headers) { return byte[].class; }
                public void handleFrame(StompHeaders headers, Object body) { long now = System.nanoTime(); inbox.add(new Envelope(JSON.readTree((byte[]) body), now)); }
            });
            CompletableFuture<Boolean> ready = new CompletableFuture<>();
            var subscription = session.subscribe("/user/queue/sync", new StompFrameHandler() {
                public Type getPayloadType(StompHeaders headers) { return byte[].class; }
                public void handleFrame(StompHeaders headers, Object body) { ready.complete(JSON.readTree((byte[]) body).get("ready").asBoolean()); }
            });
            send("/app/chat.sync", Map.of()); assertTrue(ready.get(5, TimeUnit.SECONDS)); subscription.unsubscribe();
        }
        void send(String destination, Map<String, ?> body) { StompHeaders headers = new StompHeaders(); headers.setDestination(destination); headers.setContentType(MediaType.APPLICATION_JSON); session.send(headers, JSON.writeValueAsBytes(body)); }
        void disconnect() { if (session != null && session.isConnected()) session.disconnect(); if (socket != null) socket.stop(); if (scheduler != null) scheduler.shutdown(); }
        public void close() { disconnect(); http.close(); }
    }
}
