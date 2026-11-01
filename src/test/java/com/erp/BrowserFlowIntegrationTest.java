package com.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import com.sun.net.httpserver.HttpServer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BrowserFlowIntegrationTest.BrowserFixtures.class)
class BrowserFlowIntegrationTest {

    private static final Path UPLOADS = temporaryUploads();
    private static final AtomicReference<CountDownLatch> MODEL_COMPLETION = new AtomicReference<>();
    private static final HttpServer MODEL = modelServer();

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
        registry.add("erp.upload-dir", UPLOADS::toString);
        registry.add("erp.ai.base-url", () -> "http://127.0.0.1:" + MODEL.getAddress().getPort());
        registry.add("erp.ai.model", () -> "browser-fixture");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void chromiumRunsCoreUserFlowsAgainstIsolatedMySql() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String encodedPassword = passwords.encode("browser-test-password");
        createUser("e2e-" + suffix + "-draft", "E2E사원" + suffix, "사원", 400, encodedPassword);
        createUser("e2e-" + suffix + "-first", "E2E대리" + suffix, "대리", 300, encodedPassword);
        createUser("e2e-" + suffix + "-second", "E2E팀장" + suffix, "팀장", 200, encodedPassword);
        createUser("e2e-" + suffix + "-password", "E2E암호" + suffix, "사원", 400, encodedPassword);
        createUser("e2e-" + suffix + "-admin", "E2E관리자" + suffix, "팀장", 200, encodedPassword);
        jdbc.update("UPDATE user SET authority=TRUE WHERE usernum=?", "e2e-" + suffix + "-admin");
        createUser("e2e-" + suffix + "-form-a", "E2E수정A" + suffix, "사원", 400, encodedPassword);
        createUser("e2e-" + suffix + "-form-b", "E2E수정B" + suffix, "대리", 300, encodedPassword);
        jdbc.update("INSERT INTO notice (notice_title,notice_content,usernum) VALUES (?,?,?)",
                "브라우저 AI 공지 999999 " + suffix, "시연 자료는 금요일까지 제출합니다.", "e2e-" + suffix + "-admin");
        jdbc.update("INSERT INTO notice_team (notice_no,notice_team) SELECT notice_no,'999' FROM notice WHERE notice_title=?",
                "브라우저 AI 공지 999999 " + suffix);

        int repeats = Integer.parseInt(System.getenv().getOrDefault("ERP_E2E_REPEAT", "1"));
        assertThat(repeats).isBetween(1, 20);
        Path log = Path.of("target/browser-flow.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        ProcessBuilder command = new ProcessBuilder("npm", "run", "test:e2e", "--", "--repeat-each=" + repeats)
                .directory(Path.of("frontend").toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        command.environment().put("ERP_E2E_BASE_URL", "http://127.0.0.1:" + port);
        command.environment().put("ERP_E2E_USER_SUFFIX", suffix);
        Process process = command.start();
        try {
            assertThat(process.waitFor(8, TimeUnit.MINUTES))
                    .as("Playwright finishes; failures retain traces/screenshots in frontend/test-results")
                    .isTrue();
            int exit = process.exitValue();
            assertThat(exit).as("Playwright browser flow result; output: %s%n%s",
                    log, exit == 0 ? "" : Files.readString(log)).isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            try (var files = Files.walk(UPLOADS)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private void createUser(String id, String name, String level, int order, String password) {
        jdbc.update("""
                INSERT INTO user (usernum,userpw,name,team,level,level_num,firstlogin,authority,user_status,
                    idnum1,idnum2,phonenum,officenum,email,joindate)
                VALUES (?,?,?,'개발',?,?,FALSE,FALSE,'재직','900101','1','010-0000-0000','02-000-0000','browser@example.test','2026-01-01')
                """, id, password, name, level, order);
    }

    private static Path temporaryUploads() {
        try {
            return Files.createTempDirectory("erp-browser-uploads-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static HttpServer modelServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/generate", exchange -> {
                try (exchange) {
                    String prompt = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (prompt.contains("모델 오류 유도")) { exchange.sendResponseHeaders(503, -1); return; }
                    CountDownLatch completion = new CountDownLatch(1);
                    MODEL_COMPLETION.set(completion);
                    exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write("{\"response\":\"시연 자료는 \",\"done\":false}\n".getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    try {
                        if (!completion.await(15, TimeUnit.SECONDS)) return;
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                    exchange.getResponseBody().write("{\"response\":\"금요일까지 제출합니다.\",\"done\":true,\"eval_count\":9}\n".getBytes(StandardCharsets.UTF_8));
                }
            });
            server.start();
            return server;
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    @AfterAll
    static void stopModel() { MODEL.stop(0); }

    @TestConfiguration(proxyBeanMethods = false)
    static class BrowserFixtures {
        @Bean
        SessionTimeoutProbe sessionTimeoutProbe() {
            return new SessionTimeoutProbe();
        }
    }

    // Imported only by this test. Nothing is added to the production application.
    @TestComponent
    @RestController
    static class SessionTimeoutProbe {
        private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

        @GetMapping(value = "/api/session", params = "e2eAi=complete")
        Map<String, Boolean> completeModel(HttpServletRequest request) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("loginUser") == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            CountDownLatch completion = MODEL_COMPLETION.get();
            if (completion == null) throw new ResponseStatusException(HttpStatus.CONFLICT);
            completion.countDown();
            return Map.of("completed", true);
        }

        @GetMapping(value = "/api/session", params = "e2eTimeout=arm")
        Map<String, String> arm(HttpServletRequest request) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("loginUser") == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            session.setMaxInactiveInterval(1);
            String probe = UUID.randomUUID().toString();
            sessions.put(probe, session);
            return Map.of("probe", probe);
        }

        @GetMapping(value = "/api/session", params = "e2eTimeout=idle")
        Map<String, Boolean> idle(@RequestParam String probe) {
            HttpSession session = sessions.get(probe);
            if (session == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            boolean reached;
            try {
                reached = System.currentTimeMillis() - session.getLastAccessedTime()
                        >= TimeUnit.SECONDS.toMillis(session.getMaxInactiveInterval());
            } catch (IllegalStateException expired) {
                reached = true;
            }
            if (reached) sessions.remove(probe);
            return Map.of("idleTimeoutReached", reached);
        }
    }
}
