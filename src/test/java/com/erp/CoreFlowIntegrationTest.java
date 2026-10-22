package com.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
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
import java.security.MessageDigest;
import java.time.Year;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import com.erp.mapper.LoginMapper;
import com.erp.mapper.UserMapper;
import com.erp.service.ApprovalService;
import com.erp.service.AiService;
import com.erp.service.ChatService;
import com.erp.service.LocalLlmClient;
import com.erp.service.NoticeService;
import com.erp.service.UserService;
import com.erp.service.LocalLlmClient.GenerationStats;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.noticeVO;
import com.erp.vo.userVO;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class CoreFlowIntegrationTest {

	private static final Pattern CSRF_JSON = Pattern.compile(
			"\"parameterName\":\"([^\"]+)\",\"headerName\":\"([^\"]+)\",\"token\":\"([^\"]+)\"");

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
	private LoginMapper loginMapper;

	@Autowired
	private ApprovalService approvalService;

	@Autowired
	private AiService aiService;

	@Autowired
	private ChatService chatService;

	@Autowired
	private UserService userService;

	@Autowired
	private NoticeService noticeService;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private PlatformTransactionManager transactions;

	@MockitoBean
	private LocalLlmClient localLlmClient;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JsonMapper json;

	@Value("${erp.upload-dir}")
	private String uploadDirectory;

	private HttpClient client;

	@BeforeEach
	void createClient() {
		client = newClient();
	}

	@Test
	@Order(1)
	void reactShellAndLoginSessionAreAccessibleWithoutAuthentication() throws Exception {
		HttpResponse<String> landing = get(client, "/");
		HttpResponse<String> login = get(client, "/login/login.do");
		HttpResponse<String> session = get(client, "/api/session");

		assertThat(landing.statusCode()).isEqualTo(200);
		assertThat(landing.body()).contains("id=\"root\"", "resources/app/app.js");
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).contains("id=\"root\"", "resources/app/app.js");
		assertThat(session.statusCode()).isEqualTo(200);
		assertThat(session.body()).contains("\"authenticated\":false", "\"csrf\"");
	}

	@Test
	@Order(2)
	void protectedPageRedirectsToLogin() throws Exception {
		HttpResponse<String> response = get(client, "/main.do");

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("location").orElse(""))
				.endsWith("/ERP/login/login.do");
	}

	@Test
	@Order(3)
	void invalidCredentialsAreRejected() throws Exception {
		HttpResponse<String> response = login(client, "admin", "wrong-password");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).isEqualTo("ERROR");
	}

	@Test
	@Order(4)
	void legacyPasswordIsUpgradedAndCorePagesOpen() throws Exception {
		loginMapper.updatePassword("admin", md5("1234"));

		HttpResponse<String> login = login(client, "admin", "1234");
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).isEqualTo("OK");
		assertThat(loginMapper.findByUsernum("admin").getUserpw()).startsWith("{bcrypt}");

		assertThat(get(client, "/main.do").statusCode()).isEqualTo(200);
		assertThat(get(client, "/notice/list.do").statusCode()).isEqualTo(200);
		assertThat(get(client, "/approval/list.do").statusCode()).isEqualTo(200);
	}

	@Test
	@Order(5)
	void draftCreatesOrderedApprovalLines() {
		int approvalNo = createDraft("결재선 지정", "2005004", List.of("2005003", "2005002"));

		approvalVO approval = approvalService.getApproval(approvalNo);
		List<approval_line_VO> lines = approvalService.getLines(approvalNo);
		assertThat(approval.getDocument_status()).isEqualTo("대기중");
		assertThat(lines).extracting(approval_line_VO::getApproval_target)
				.containsExactly("2005003", "2005002");
		assertThat(lines).extracting(approval_line_VO::getApproval_sort)
				.containsExactly("1", "2");
		assertThat(lines).extracting(approval_line_VO::getApproval_status)
				.containsOnly("대기");
	}

	@Test
	@Order(6)
	void approvalsMoveDocumentFromPendingToProgressAndApproved() throws Exception {
		int approvalNo = createDraft("단계별 승인", "2005004", List.of("2005003", "2005002"));
		HttpClient firstApprover = authenticatedClient("2005003");
		HttpClient secondApprover = authenticatedClient("2005002");

		assertThat(get(secondApprover, "/api/approvals/" + approvalNo).body())
				.contains("\"canApprove\":false");
		assertThat(decide(secondApprover, approvalNo, "승인", "순서 건너뛰기").statusCode()).isEqualTo(403);
		assertThat(decide(secondApprover, approvalNo, "반려", "순서 건너뛰기").statusCode()).isEqualTo(403);
		assertThat(approvalService.getLines(approvalNo))
				.extracting(approval_line_VO::getApproval_status).containsOnly("대기");

		assertThat(decide(firstApprover, approvalNo, "승인", "1차 승인").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("진행중");
		assertThat(get(secondApprover, "/api/approvals/" + approvalNo).body())
				.contains("\"canApprove\":true");

		assertThat(decide(secondApprover, approvalNo, "승인", "최종 승인").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("승인");
		assertThat(decide(secondApprover, approvalNo, "승인", "중복 승인").statusCode()).isEqualTo(403);
		assertThat(approvalService.getLines(approvalNo).getLast().getComment()).isEqualTo("최종 승인");
	}

	@Test
	@Order(7)
	void rejectionEndsDocumentAndRequiresComment() throws Exception {
		int approvalNo = createDraft("반려 처리", "2005004", List.of("2005002", "2005003"));
		HttpClient approver = authenticatedClient("2005002");
		HttpClient laterApprover = authenticatedClient("2005003");

		assertThat(postForm(approver, "/approval/updateComment.do",
				Map.of("approval_no", String.valueOf(approvalNo), "approval_status", "반려", "comment", ""))
				.statusCode()).isEqualTo(400);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("대기중");

		assertThat(decide(approver, approvalNo, "반려", "내용 보완 필요").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("반려");
		assertThat(get(laterApprover, "/api/approvals/" + approvalNo).body())
				.contains("\"canApprove\":false");
		assertThat(decide(laterApprover, approvalNo, "승인", "반려 후 승인").statusCode()).isEqualTo(403);
		assertThat(approvalService.getLines(approvalNo))
				.extracting(approval_line_VO::getApproval_status).containsExactly("반려", "대기");

		approvalVO revised = draft(uniqueTitle("반려 보완 후 재상신"));
		revised.setApproval_no(approvalNo);
		approvalService.modifyApproval(revised, "2005004", List.of(), List.of("2005002", "2005003"));
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("대기중");
		assertThat(approvalService.getLines(approvalNo))
				.extracting(approval_line_VO::getApproval_status).containsExactly("대기", "대기");
		assertThat(approvalService.getLines(approvalNo))
				.allSatisfy(line -> {
					assertThat(line.getComment()).isNull();
					assertThat(line.getApproval_date()).isNull();
				});
		assertThat(decide(laterApprover, approvalNo, "승인", "재상신 순서 건너뛰기").statusCode()).isEqualTo(403);
		assertThat(decide(approver, approvalNo, "승인", "보완 확인").statusCode()).isEqualTo(302);
		assertThat(decide(laterApprover, approvalNo, "승인", "최종 확인").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("승인");
	}

	@Test
	@Order(8)
	void unauthorizedUserCannotApprove() {
		int approvalNo = createDraft("권한 없는 승인", "2005004", List.of("2005002"));

		assertThatThrownBy(() -> approvalService.processApproval(
				approvalNo, "2005007", "승인", "권한 없음"))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("대기중");
	}

	@Test
	@Order(9)
	void drafterAndApproverCanViewBeforeFinalApprovalAndEveryoneCanViewAfter() throws Exception {
		int approvalNo = createDraft("문서 열람 권한", "2005004", List.of("2005002"));
		String view = "/approval/view.do?approval_no=" + approvalNo;
		HttpClient drafter = authenticatedClient("2005004");
		HttpClient approver = authenticatedClient("2005002");
		HttpClient employee = authenticatedClient("2005007");

		assertThat(get(drafter, view).statusCode()).isEqualTo(200);
		assertThat(get(approver, view).statusCode()).isEqualTo(200);
		assertThat(get(employee, view).statusCode()).isEqualTo(403);

		assertThat(decide(approver, approvalNo, "승인", "공개 승인").statusCode()).isEqualTo(302);
		assertThat(get(employee, view).statusCode()).isEqualTo(200);
	}

	@Test
	@Order(10)
	void invalidApproverRollsBackEntireDraft() {
		String title = uniqueTitle("롤백 검증");
		approvalVO draft = draft(title);

		assertThatThrownBy(() -> approvalService.createApproval(
				draft, "2005004", List.of(), List.of("없는사번")))
				.isInstanceOf(IllegalArgumentException.class);
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from approval where approval_title = ?", Integer.class, title);
		assertThat(count).isZero();
	}

	@Test
	@Order(11)
	void employeeManagementIsAdminOnlyAndDoesNotExposePassword() throws Exception {
		HttpClient employee = authenticatedClient("2005007");
		HttpClient admin = authenticatedClient("admin");

		assertThat(get(employee, "/user/list.do").statusCode()).isEqualTo(403);
		assertThat(get(employee, "/user/view.do?usernum=2005004").statusCode()).isEqualTo(403);
		assertThat(get(employee, "/user/write.do").statusCode()).isEqualTo(403);
		assertThat(get(employee, "/user/myinfo.do").statusCode()).isEqualTo(200);
		assertThat(get(employee, "/api/users").statusCode()).isEqualTo(403);
		assertThat(get(employee, "/api/users/me").body())
				.contains("\"usernum\":\"2005007\"")
				.doesNotContain("userpw");

		HttpResponse<String> detail = get(admin, "/user/view.do?usernum=2005004");
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(detail.body()).doesNotContain("사원PW", "{bcrypt}");
		assertThat(get(admin, "/user/list.do").statusCode()).isEqualTo(200);
		assertThat(get(admin, "/api/users").body()).contains("\"items\"", "\"totalCount\"");
		assertThat(get(admin, "/api/users/2005004").body())
				.contains("\"usernum\":\"2005004\"")
				.doesNotContain("userpw");
	}

	@Test
	@Order(12)
	void adminCanCreateModifyAndDeleteEmployee() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		String usernum = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String create = """
				{"usernum":"%s","userpw":"Initial-1234!","name":"테스트사원",
				 "idnum1":"990101","idnum2":"1","phonenum":"010-1111-2222",
				 "officenum":"02-111-2222","email":"test@example.com","team":"개발",
				 "level":"사원","joindate":"2026-09-18","authority":false,"userStatus":"재직"}
				""".formatted(usernum);

		assertThat(sendJson(admin, "POST", "/api/users", create).statusCode())
				.isEqualTo(200);
		Map<String, Object> inserted = jdbcTemplate.queryForMap(
				"select userpw, firstlogin, level_num from user where usernum = ?", usernum);
		assertThat(inserted.get("userpw").toString()).startsWith("{bcrypt}");
		assertThat(inserted.get("firstlogin")).isEqualTo(true);
		assertThat(inserted.get("level_num")).isEqualTo(400);
		assertThat(sendJson(admin, "POST", "/api/users", create).statusCode()).isEqualTo(409);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from user where usernum = ?", Integer.class, usernum)).isEqualTo(1);

		String update = """
				{"userpw":"","name":"수정사원","idnum1":"990101","idnum2":"1",
				 "phonenum":"010-3333-4444","officenum":"02-333-4444",
				 "email":"updated@example.com","team":"개발","level":"대리",
				 "joindate":"2026-09-18","authority":false,"userStatus":"재직"}
				""";

		assertThat(sendJson(admin, "PUT", "/api/users/" + usernum, update).statusCode()).isEqualTo(200);
		assertThat(jdbcTemplate.queryForObject(
				"select concat(name, ':', level_num) from user where usernum = ?", String.class, usernum))
				.isEqualTo("수정사원:300");

		assertThat(sendJson(admin, "DELETE", "/api/users/" + usernum, "{}").statusCode()).isEqualTo(200);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from user where usernum = ?", Integer.class, usernum)).isZero();
	}

	@Test
	@Order(13)
	void noticeChangesAreAdminOnly() throws Exception {
		HttpClient employee = authenticatedClient("2005007");
		HttpClient admin = authenticatedClient("admin");

		assertThat(get(employee, "/notice/write.do").statusCode()).isEqualTo(403);
		assertThat(postForm(employee, "/notice/delete.do",
				Map.of("notice_no", "1")).statusCode()).isEqualTo(403);
		assertThat(get(employee, "/notice/view.do?notice_no=1").statusCode()).isEqualTo(200);
		assertThat(get(admin, "/notice/write.do").statusCode()).isEqualTo(200);

		String title = uniqueTitle("관리자 공지");
		assertThat(postForm(admin, "/notice/writeOK.do", Map.of(
				"notice_title", title,
				"notice_content", "관리자 전용 공지",
				"notice_team", "100")).statusCode()).isEqualTo(302);
		Integer noticeNo = jdbcTemplate.queryForObject(
				"select notice_no from notice where notice_title = ?", Integer.class, title);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from notice_team where notice_no = ?", Integer.class, noticeNo))
				.isEqualTo(1);
		assertThat(postForm(admin, "/notice/delete.do",
				Map.of("notice_no", String.valueOf(noticeNo))).statusCode()).isEqualTo(302);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from notice where notice_no = ?", Integer.class, noticeNo)).isZero();
	}

	@Test
	@Order(14)
	void anotherUserCannotModifyDraft() throws Exception {
		int approvalNo = createDraft("타인 수정 차단", "2005004", List.of("2005002"));
		HttpClient other = authenticatedClient("2005007");

		assertThat(get(other, "/approval/modify.do?approval_no=" + approvalNo).statusCode())
				.isEqualTo(403);
		assertThat(postForm(other, "/approval/modify.do", Map.of(
				"approval_no", String.valueOf(approvalNo),
				"kind", "기안서",
				"approval_code", "FORGED",
				"approval_title", "위조 수정",
				"approval_content", "위조 내용",
				"approval_target", "2005002")).statusCode()).isEqualTo(403);
		assertThat(approvalService.getApproval(approvalNo).getApproval_title())
				.doesNotContain("위조");
	}

	@Test
	@Order(15)
	void approvalAttachmentRequiresDocumentAccess() throws Exception {
		Path uploadDirectory = Path.of(System.getProperty("java.io.tmpdir"), "erp-test-uploads");
		Files.createDirectories(uploadDirectory);
		String savedName = UUID.randomUUID().toString();
		Path stored = uploadDirectory.resolve(savedName);
		Files.writeString(stored, "내부 결재 첨부파일", StandardCharsets.UTF_8);

		approval_file_VO file = new approval_file_VO();
		file.setApname(savedName);
		file.setAfname("internal.txt");
		int approvalNo = approvalService.createApproval(
				draft(uniqueTitle("첨부 권한")), "2005004", List.of(file), List.of("2005002"));

		try {
			HttpClient approver = authenticatedClient("2005002");
			HttpClient employee = authenticatedClient("2005007");
			String download = "/approval/down.do?no=" + approvalNo;

			assertThat(get(employee, download).statusCode()).isEqualTo(403);
			HttpResponse<String> allowed = get(approver, download);
			assertThat(allowed.statusCode()).isEqualTo(200);
			assertThat(allowed.body()).isEqualTo("내부 결재 첨부파일");
		} finally {
			Files.deleteIfExists(stored);
		}
	}

	@Test
	@Order(16)
	void passwordChangeRejectsWrongCurrentPasswordAndUsesBcrypt() throws Exception {
		HttpClient employee = authenticatedClient("2005009");

		HttpResponse<String> rejected = sendJson(employee, "POST", "/api/users/me/password", """
				{"currentPassword":"wrong","newPassword":"Changed-1234!","confirmPassword":"Changed-1234!"}
				""");
		assertThat(rejected.statusCode()).isEqualTo(400);

		HttpResponse<String> changed = sendJson(employee, "POST", "/api/users/me/password", """
				{"currentPassword":"1234","newPassword":"Changed-1234!","confirmPassword":"Changed-1234!"}
				""");
		assertThat(changed.statusCode()).isEqualTo(200);
		assertThat(changed.body()).contains("비밀번호가 변경되었습니다");
		assertThat(loginMapper.findByUsernum("2005009").getUserpw()).startsWith("{bcrypt}");
		assertThat(login(newClient(), "2005009", "1234").statusCode()).isEqualTo(401);
		assertThat(login(newClient(), "2005009", "Changed-1234!").statusCode()).isEqualTo(200);
	}

	@Test
	@Order(17)
	void reactFrontendApisReturnJsonWithoutSensitiveUserFields() throws Exception {
		HttpClient admin = authenticatedClient("admin");

		HttpResponse<String> session = get(admin, "/api/session");
		assertThat(session.statusCode()).isEqualTo(200);
		assertThat(session.headers().firstValue("content-type").orElse(""))
				.contains("application/json");
		assertThat(session.body())
				.contains("\"authenticated\":true", "\"usernum\":\"admin\"", "\"csrf\"")
				.doesNotContain("userpw", "idnum1", "idnum2");

		assertThat(get(admin, "/api/dashboard").body())
				.contains("\"counts\"", "\"notices\"");
		assertThat(get(admin, "/api/approvals?mode=0").body())
				.contains("\"items\"", "\"totalCount\"");
		assertThat(get(admin, "/api/approvals/received").body())
				.contains("\"items\"", "\"mode\":\"received\"");
		assertThat(get(admin, "/api/approvals/completed").body())
				.contains("\"items\"", "\"mode\":\"completed\"");
		assertThat(get(admin, "/api/approvals/write-options").body())
				.contains("\"drafter\"", "\"approvers\"");
		assertThat(get(admin, "/api/notices").body())
				.contains("\"items\"", "\"totalCount\"");
		assertThat(get(admin, "/api/notices/1").body())
				.contains("\"noticeNo\":1", "\"teams\"");
	}

	@Test
	@Order(18)
	void messengerPersistsUnreadStateAndBlocksNonParticipants() throws Exception {
		long roomNo = chatService.openRoom("2005004", "2005007");
		String content = uniqueTitle("재접속 후에도 남는 메시지");
		chatService.sendMessage(roomNo, "2005004", content);

		assertThat(chatService.getRooms("2005007"))
				.filteredOn(room -> room.getRoomNo() == roomNo)
				.singleElement()
				.extracting("unreadCount")
				.isEqualTo(1);

		HttpClient recipient = authenticatedClient("2005007");
		HttpClient outsider = authenticatedClient("2005002");
		assertThat(get(recipient, "/api/chat/rooms/" + roomNo + "/messages").body())
				.contains(content);
		assertThat(get(outsider, "/api/chat/rooms/" + roomNo + "/messages").statusCode())
				.isEqualTo(403);

		HttpResponse<String> read = postForm(recipient, "/api/chat/rooms/" + roomNo + "/read", Map.of());
		assertThat(read.statusCode()).isEqualTo(200);
		assertThat(read.body()).contains("\"updated\":1");
		assertThat(chatService.getMessages(roomNo, "2005007").getLast().getReadAt())
				.isNotBlank();
	}

	@Test
	@Order(19)
	void localAiStreamsAuthorizedContextAndHandlesModelFailure() throws Exception {
		String allowedApproval = uniqueTitle("AI 허용 결재");
		String hiddenApproval = uniqueTitle("AI 차단 결재");
		createDraft(allowedApproval, "2005004", List.of("2005002"));
		createDraft(hiddenApproval, "2005004", List.of("2005003"));

		String allowedNotice = uniqueTitle("AI 개발 공지");
		String hiddenNotice = uniqueTitle("AI 디자인 공지");
		int allowedNoticeNo = insertNotice(allowedNotice, "100");
		insertNotice(hiddenNotice, "200");

		AtomicReference<String> prompt = new AtomicReference<>();
		doAnswer(invocation -> {
			prompt.set(invocation.getArgument(0));
			Consumer<String> consumer = invocation.getArgument(1);
			consumer.accept("테스트 ");
			consumer.accept("응답");
			return new GenerationStats("fake-model", 5, 12, 2);
		}).when(localLlmClient).generate(anyString(), any());

		HttpClient employee = authenticatedClient("2005002");
		HttpResponse<String> response = sendJson(employee, "POST", "/api/ai/chat",
				"{\"question\":\"내 업무를 알려줘\"}");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("content-type").orElse(""))
				.contains("text/event-stream");
		assertThat(response.body())
				.contains("event:token", "테스트", "응답", "event:done", "fake-model");
		assertThat(prompt.get())
				.contains(allowedApproval, allowedNotice)
				.doesNotContain(hiddenApproval, hiddenNotice);

		doThrow(new IOException("internal model error"))
				.when(localLlmClient).generate(anyString(), any());
		HttpResponse<String> failed = sendJson(employee, "POST", "/api/ai/chat",
				"{\"question\":\"최근 공지를 알려줘\"}");
		assertThat(failed.body())
				.contains("event:error", "로컬 AI가 응답하지 않습니다")
				.doesNotContain("internal model error");

		jdbcTemplate.update("delete from notice where notice_no = ?", allowedNoticeNo);
	}

	@Test
	@Order(20)
	void concurrentDuplicateAndConflictingDecisionsHaveOneWinner() throws Exception {
		for (String secondDecision : List.of("승인", "반려")) {
			int approvalNo = createDraft("동시 결재 " + secondDecision, "2005004", List.of("2005002"));
			List<Boolean> results = concurrently(
					() -> tryDecision(approvalNo, "승인", "첫 번째 요청"),
					() -> tryDecision(approvalNo, secondDecision, "두 번째 요청"));

			assertThat(results).containsExactlyInAnyOrder(true, false);
			String winningDecision = results.getFirst() ? "승인" : secondDecision;
			assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo(winningDecision);
			assertThat(approvalService.getLines(approvalNo)).singleElement().satisfies(line -> {
				assertThat(line.getApproval_status()).isEqualTo(winningDecision);
				assertThat(line.getComment()).isEqualTo(results.getFirst() ? "첫 번째 요청" : "두 번째 요청");
				assertThat(line.getApproval_date()).isNotBlank();
			});
		}
	}

	@Test
	@Order(21)
	void failedDraftModificationAndResubmissionRestoreEntireDocument() {
		approval_file_VO originalFile = new approval_file_VO();
		originalFile.setApname(UUID.randomUUID().toString());
		originalFile.setAfname("original.txt");
		String originalTitle = uniqueTitle("수정 롤백");
		int approvalNo = approvalService.createApproval(draft(originalTitle), "2005004",
				List.of(originalFile), List.of("2005002", "2005003"));

		for (String status : List.of("대기중", "반려")) {
			if ("반려".equals(status)) {
				approvalService.processApproval(approvalNo, "2005002", "반려", "기존 반려 의견");
			}
			Map<String, Object> originalDocument = jdbcTemplate.queryForMap(
					"select * from approval where approval_no = ?", approvalNo);
			List<Map<String, Object>> originalLines = jdbcTemplate.queryForList(
					"select * from approval_line where approval_no = ? order by approval_sort", approvalNo);
			approvalVO replacement = draft(uniqueTitle("실패할 수정"));
			replacement.setApproval_no(approvalNo);
			approval_file_VO replacementFile = new approval_file_VO();
			replacementFile.setApname(UUID.randomUUID().toString());
			replacementFile.setAfname("replacement.txt");

			assertThatThrownBy(() -> approvalService.modifyApproval(replacement, "2005004",
					List.of(replacementFile), List.of("2005003", "없는사번")))
					.isInstanceOf(IllegalArgumentException.class);
			assertThat(jdbcTemplate.queryForMap("select * from approval where approval_no = ?", approvalNo))
					.isEqualTo(originalDocument);
			assertThat(jdbcTemplate.queryForList(
					"select * from approval_line where approval_no = ? order by approval_sort", approvalNo))
					.isEqualTo(originalLines);

			assertThatThrownBy(() -> approvalService.modifyApproval(replacement, "2005004",
					List.of(replacementFile, replacementFile), List.of("2005003", "2005002")))
					.isInstanceOf(DataAccessException.class);
			assertThat(jdbcTemplate.queryForMap("select * from approval where approval_no = ?", approvalNo))
					.isEqualTo(originalDocument);
			assertThat(jdbcTemplate.queryForList(
					"select * from approval_line where approval_no = ? order by approval_sort", approvalNo))
					.isEqualTo(originalLines);
			assertThat(approvalService.getFiles(approvalNo)).singleElement().satisfies(file -> {
				assertThat(file.getApname()).isEqualTo(originalFile.getApname());
				assertThat(file.getAfname()).isEqualTo("original.txt");
			});
		}
	}

	@Test
	@Order(22)
	void approvalAndDraftModificationCannotResetAnApprovedLine() throws Exception {
		int approvalNo = createDraft("승인과 수정 경합", "2005004", List.of("2005002", "2005003"));
		String originalTitle = approvalService.getApproval(approvalNo).getApproval_title();
		approvalVO replacement = draft(uniqueTitle("동시 수정"));
		replacement.setApproval_no(approvalNo);
		List<Boolean> results = concurrently(
				() -> tryDecision(approvalNo, "승인", "동시 승인"),
				() -> {
					try {
						approvalService.modifyApproval(replacement, "2005004", List.of(),
								List.of("2005002", "2005003"));
						return true;
					} catch (AccessDeniedException exception) {
						return false;
					}
				});

		assertThat(results.getFirst()).isTrue();
		approvalVO updated = approvalService.getApproval(approvalNo);
		assertThat(updated.getDocument_status()).isEqualTo("진행중");
		assertThat(updated.getApproval_title())
				.isEqualTo(results.getLast() ? replacement.getApproval_title() : originalTitle);
		assertThat(approvalService.getLines(approvalNo))
				.extracting(approval_line_VO::getApproval_status).containsExactly("승인", "대기");
		assertThat(approvalService.getLines(approvalNo).getFirst().getComment()).isEqualTo("동시 승인");
	}

	@Test
	@Order(23)
	void concurrentEmployeeRegistrationsAllocateDistinctNumbersFromSamePreview() throws Exception {
		HttpClient firstAdmin = authenticatedClient("admin");
		HttpClient secondAdmin = authenticatedClient("admin");
		String preview = get(firstAdmin, "/api/users/next-number").body();
		assertThat(get(secondAdmin, "/api/users/next-number").body()).isEqualTo(preview);
		String previewNumber = userNumber(preview);
		String name = "동시등록-" + UUID.randomUUID().toString().substring(0, 8);
		String create = employeeRegistration(previewNumber, name);

		try {
			List<HttpResponse<String>> responses = concurrently(
					() -> sendJson(firstAdmin, "POST", "/api/users", create),
					() -> sendJson(secondAdmin, "POST", "/api/users", create));
			assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
			String firstNumber = userNumber(responses.getFirst().body());
			String secondNumber = userNumber(responses.getLast().body());
			assertThat(firstNumber).matches(previewNumber.substring(0, 4) + "\\d{3}");
			assertThat(secondNumber).matches(previewNumber.substring(0, 4) + "\\d{3}").isNotEqualTo(firstNumber);
			assertThat(jdbcTemplate.queryForList("select usernum from user where name = ?", String.class, name))
					.containsExactlyInAnyOrder(firstNumber, secondNumber);
		} finally {
			jdbcTemplate.update("delete from user where name = ?", name);
		}
	}

	@Test
	@Order(24)
	void exhaustedEmployeeNumbersAreNotReusedAfterDeletion() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		String year = String.valueOf(Year.now().getValue());
		String lastNumber = year + "999";
		String name = "번호한계-" + UUID.randomUUID().toString().substring(0, 8);
		String create = employeeRegistration("", name);
		List<Integer> originalSequence = jdbcTemplate.queryForList(
				"select last_number from user_number_sequence where number_year = ?", Integer.class, year);
		int originalUserCount = jdbcTemplate.queryForObject("select count(*) from user", Integer.class);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from user where usernum = ?", Integer.class, lastNumber)).isZero();

		try {
			jdbcTemplate.update("insert into user_number_sequence (number_year, last_number) values (?, 998) "
					+ "on duplicate key update last_number = 998", year);
			assertThat(userNumber(get(admin, "/api/users/next-number").body())).isEqualTo(lastNumber);
			HttpResponse<String> registered = sendJson(admin, "POST", "/api/users", create);
			assertThat(registered.statusCode()).isEqualTo(200);
			assertThat(userNumber(registered.body())).isEqualTo(lastNumber);
			Map<String, Object> lastEmployee = jdbcTemplate.queryForMap(
					"select * from user where usernum = ?", lastNumber);

			HttpResponse<String> exhausted = sendJson(admin, "POST", "/api/users", create);
			assertThat(exhausted.statusCode()).isEqualTo(400);
			assertThat(exhausted.body()).contains("001~999");
			assertThat(jdbcTemplate.queryForObject("select count(*) from user", Integer.class))
					.isEqualTo(originalUserCount + 1);
			assertThat(jdbcTemplate.queryForMap("select * from user where usernum = ?", lastNumber))
					.isEqualTo(lastEmployee);
			assertThat(jdbcTemplate.queryForObject(
					"select last_number from user_number_sequence where number_year = ?", Integer.class, year))
					.isEqualTo(999);

			assertThat(sendJson(admin, "DELETE", "/api/users/" + lastNumber, "{}").statusCode()).isEqualTo(200);
			assertThat(get(admin, "/api/users/next-number").statusCode()).isEqualTo(400);
			assertThat(sendJson(admin, "POST", "/api/users", create).statusCode()).isEqualTo(400);
			assertThat(jdbcTemplate.queryForObject("select count(*) from user", Integer.class))
					.isEqualTo(originalUserCount);
			assertThat(jdbcTemplate.queryForObject(
					"select count(*) from user where usernum = ?", Integer.class, lastNumber)).isZero();
			assertThat(jdbcTemplate.queryForObject(
					"select last_number from user_number_sequence where number_year = ?", Integer.class, year))
					.isEqualTo(999);
		} finally {
			jdbcTemplate.update("delete from user where name = ?", name);
			if (originalSequence.isEmpty()) {
				jdbcTemplate.update("delete from user_number_sequence where number_year = ?", year);
			} else {
				jdbcTemplate.update("update user_number_sequence set last_number = ? where number_year = ?",
						originalSequence.getFirst(), year);
			}
		}
	}

	private static String employeeRegistration(String usernum, String name) {
		return """
				{"usernum":"%s","userpw":"Initial-1234!","name":"%s",
				 "idnum1":"990101","idnum2":"1","phonenum":"010-1111-2222",
				 "officenum":"02-111-2222","email":"test@example.com","team":"개발",
				 "level":"사원","joindate":"2026-09-18","authority":false,"userStatus":"재직"}
				""".formatted(usernum, name);
	}

	private boolean tryDecision(int approvalNo, String decision, String comment) {
		try {
			approvalService.processApproval(approvalNo, "2005002", decision, comment);
			return true;
		} catch (AccessDeniedException exception) {
			return false;
		}
	}

	private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<T>> futures = List.of(first, second).stream().map(task -> executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(10, TimeUnit.SECONDS)).as("동시 요청 시작 신호").isTrue();
				return task.call();
			})).toList();
			assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 요청 출발 준비").isTrue();
			start.countDown();
			return List.of(futures.getFirst().get(20, TimeUnit.SECONDS), futures.getLast().get(20, TimeUnit.SECONDS));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).as("동시 요청 종료").isTrue();
		}
	}

	@Test
	@Order(25)
	void localAiUsesOnlyRequestedAuthorizedSourcesAndFindsTitlesBeforeLimiting() throws Exception {
		String noticeTitle = uniqueTitle("AI 관련성 사내교육");
		String approvalTitle = uniqueTitle("AI 관련성 출장비");
		List<Integer> noticeNumbers = new ArrayList<>();
		List<Integer> approvalNumbers = new ArrayList<>();
		int noticeNo = insertNotice(noticeTitle, "100");
		noticeNumbers.add(noticeNo);
		int hiddenNo = insertNotice(uniqueTitle("AI 관련성 사내교육 비공개"), "200");
		noticeNumbers.add(hiddenNo);
		for (int i = 0; i < 6; i++) noticeNumbers.add(insertNotice(uniqueTitle("다른 최근 공지"), "100"));
		int approvalNo = approvalService.createApproval(draft(approvalTitle), "2005004", List.of(), List.of("2005002"));
		approvalNumbers.add(approvalNo);
		int laterNo = approvalService.createApproval(draft(uniqueTitle("AI 관련성 순서 대기")), "2005004",
				List.of(), List.of("2005003", "2005002"));
		approvalNumbers.add(laterNo);
		AtomicReference<String> prompt = new AtomicReference<>();
		doAnswer(invocation -> {
			prompt.set(invocation.getArgument(0));
			Consumer<String> consumer = invocation.getArgument(1);
			consumer.accept("자료에 근거한 답변");
			return new GenerationStats("fake-model", 1, 2, 3);
		}).when(localLlmClient).generate(anyString(), any());
		try {
			var employee = loginMapper.findByUsernum("2005002");
			aiService.answer(employee, "사내교육 공지 내용 알려줘", token -> {});
			assertThat(prompt.get()).contains(noticeTitle, "공지 " + noticeNo + "번", "통합 테스트 공지 내용")
					.doesNotContain("비공개", "다른 최근 공지", approvalTitle, "[현재 내 순서인");
			aiService.answer(employee, "결재 " + approvalNo + " 내용 알려줘", token -> {});
			assertThat(prompt.get()).contains(approvalTitle, "결재 " + approvalNo + "번", "통합 테스트 결재 내용")
					.contains("각 자료의 첫 줄을 그대로 복사")
					.doesNotContain(noticeTitle, "공지", "순서 대기", "실제 제목", "필요한 업무 사실");
			aiService.answer(employee, "공지 '" + noticeTitle + "'와 결재 '" + approvalTitle + "' 요약해줘", token -> {});
			assertThat(prompt.get()).contains("공지 " + noticeNo + "번 " + noticeTitle,
					"결재 " + approvalNo + "번 " + approvalTitle).doesNotContain("비공개", "다른 최근 공지");
			aiService.answer(employee, "최근 공지와 결재 요약해줘", token -> {});
			assertThat(prompt.get()).contains("[열람 가능한 공지", "[현재 내 순서인", approvalTitle)
					.contains("두 영역을 모두", "우선순위는 자료에서 확인할 수 없습니다", "답변에 인용하지 마라")
					.doesNotContain("순서 대기", "비공개");
			String priorityTitle = uniqueTitle("우선 처리 안내");
			noticeNumbers.add(insertNotice(priorityTitle, "100"));
			assertThat(aiService.answer(employee, "'" + priorityTitle + "' 공지 내용 알려줘", token -> {}).model())
					.isEqualTo("fake-model");
			assertThat(prompt.get()).contains(priorityTitle);

			clearInvocations(localLlmClient);
			for (String question : List.of("오늘 우선 처리할 업무가 뭐야?", "업무 우선순위 알려줘")) {
				StringBuilder priorityAnswer = new StringBuilder();
				GenerationStats priorityStats = aiService.answer(employee, question, priorityAnswer::append);
				assertThat(priorityStats.model()).isEqualTo("context-only");
				assertThat(priorityStats.evaluatedTokens()).isZero();
				assertThat(priorityAnswer.toString()).startsWith("우선순위는 자료에서 확인할 수 없습니다.")
						.contains("[공지 확인]", "다른 최근 공지", "[현재 처리 가능 결재]", "결재 " + approvalNo + "번 " + approvalTitle,
								"상태: 대기중")
						.doesNotContain("비공개", "순서 대기", "통합 테스트 공지 내용", "통합 테스트 결재 내용");
			}
			for (String question : List.of("공지 " + hiddenNo + " 내용 알려줘", "공지 999999 내용 알려줘",
					"'없는제목' 공지 내용 알려줘", "% 공지 알려줘", "결재 " + laterNo + " 내용 알려줘", "서울 날씨 알려줘")) {
				StringBuilder answer = new StringBuilder();
				GenerationStats stats = aiService.answer(employee, question, answer::append);
				assertThat(stats.model()).isEqualTo("context-only");
				assertThat(answer.toString()).doesNotContain(noticeTitle, approvalTitle, "비공개");
				assertThat(stats.evaluatedTokens()).isZero();
			}
			verify(localLlmClient, never()).generate(anyString(), any());
		} finally {
			for (int number : noticeNumbers) jdbcTemplate.update("delete from notice where notice_no = ?", number);
			for (int number : approvalNumbers) jdbcTemplate.update("delete from approval where approval_no = ?", number);
		}
	}

	@Test
	@Order(26)
	void demotionAndRetirementRevokeEveryExistingLoginAndRejectStaleServiceActors() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		for (String status : List.of("재직", "퇴직")) {
			String target = insertEmployee(true, "재직");
			try {
				HttpClient first = authenticatedClient(target);
				HttpClient second = authenticatedClient(target.toLowerCase(Locale.ROOT));
				userVO staleActor = loginMapper.findByUsernum(target);
				assertThat(get(first, "/api/users").statusCode()).isEqualTo(200);
				assertThat(sendJson(admin, "PUT", "/api/users/" + target.toLowerCase(Locale.ROOT),
						employeeUpdate("퇴직".equals(status), status)).statusCode()).isEqualTo(200);

				for (HttpClient oldSession : List.of(first, second)) {
					assertThat(get(oldSession, "/api/session").body()).contains("\"authenticated\":false");
					assertThat(get(oldSession, "/api/users").statusCode()).isIn(302, 401, 403);
					String blockedTitle = uniqueTitle("회수된 세션");
					assertThat(postForm(oldSession, "/notice/writeOK.do", Map.of(
							"notice_title", blockedTitle, "notice_content", "차단",
							"notice_team", "100")).statusCode()).isIn(302, 401, 403);
					assertThat(jdbcTemplate.queryForObject("select count(*) from notice where notice_title = ?",
							Integer.class, blockedTitle)).isZero();
				}
				assertThat(get(admin, "/api/users").statusCode()).isEqualTo(200);
				assertThatThrownBy(() -> userService.deleteUser(target, staleActor))
						.isInstanceOf(AccessDeniedException.class);
				noticeVO notice = new noticeVO();
				notice.setNotice_title(uniqueTitle("오래된 관리자 권한"));
				notice.setNotice_content("저장되면 안 되는 공지");
				assertThatThrownBy(() -> noticeService.createNotice(notice, new String[] {"100"}, staleActor))
						.isInstanceOf(AccessDeniedException.class);
				assertThat(jdbcTemplate.queryForObject("select count(*) from notice where notice_title = ?",
						Integer.class, notice.getNotice_title())).isZero();

				HttpClient fresh = newClient();
				assertThat(login(fresh, target, "1234").statusCode()).isEqualTo("퇴직".equals(status) ? 401 : 200);
				if ("재직".equals(status)) {
					assertThat(get(fresh, "/api/session").body()).contains("\"authority\":false");
					assertThat(get(fresh, "/api/users").statusCode()).isEqualTo(403);
					assertThat(get(fresh, "/api/users/me").statusCode()).isEqualTo(200);
				}
			} finally {
				jdbcTemplate.update("delete from user where usernum = ?", target);
			}
		}
	}

	@Test
	@Order(27)
	void rolledBackRoleChangeAndOrdinaryProfileEditPreserveSessions() throws Exception {
		String target = insertEmployee(true, "재직");
		try {
			HttpClient first = authenticatedClient(target);
			HttpClient second = authenticatedClient(target);
			userVO changed = userService.getUser(target);
			changed.setAuthority(false);
			new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
				userService.updateUser(changed, loginMapper.findByUsernum("admin"));
				transaction.setRollbackOnly();
			});
			assertThat(userService.getUser(target).isAuthority()).isTrue();
			for (HttpClient session : List.of(first, second)) {
				assertThat(get(session, "/api/session").body()).contains("\"authenticated\":true");
				assertThat(get(session, "/api/users").statusCode()).isEqualTo(200);
			}
			userVO profile = userService.getUser(target);
			profile.setName("일반 정보 수정");
			userService.updateUser(profile, loginMapper.findByUsernum("admin"));
			assertThat(get(first, "/api/users").statusCode()).isEqualTo(200);
			assertThat(get(second, "/api/users").statusCode()).isEqualTo(200);
		} finally {
			jdbcTemplate.update("delete from user where usernum = ?", target);
		}
	}

	@Test
	@Order(28)
	void deletingUnreferencedEmployeesRevokesSingleAndBulkSessions() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		List<String> targets = List.of(insertEmployee(false, "재직"), insertEmployee(false, "재직"),
				insertEmployee(false, "재직"));
		try {
			HttpClient first = authenticatedClient(targets.getFirst());
			HttpClient second = authenticatedClient(targets.getFirst());
			HttpClient bulkFirst = authenticatedClient(targets.get(1));
			HttpClient bulkSecond = authenticatedClient(targets.get(2));
			assertThat(sendJson(admin, "DELETE", "/api/users/" + targets.getFirst(), "{}").statusCode())
					.isEqualTo(200);
			assertThat(get(first, "/api/session").body()).contains("\"authenticated\":false");
			assertThat(get(second, "/api/session").body()).contains("\"authenticated\":false");
			assertThat(sendJson(admin, "POST", "/api/users/bulk-delete",
					"[\"" + targets.get(1) + "\",\"" + targets.get(2) + "\"]").statusCode()).isEqualTo(200);
			assertThat(get(bulkFirst, "/api/session").body()).contains("\"authenticated\":false");
			assertThat(get(bulkSecond, "/api/session").body()).contains("\"authenticated\":false");
			for (String target : targets) assertThat(login(newClient(), target, "1234").statusCode()).isEqualTo(401);
			assertThat(get(admin, "/api/users").statusCode()).isEqualTo(200);
		} finally {
			for (String target : targets) jdbcTemplate.update("delete from user where usernum = ?", target);
		}
	}

	@Test
	@Order(29)
	void invalidApproversAreRejectedBeforeDraftCreationOrModificationAndAliasesAreCanonicalized() throws Exception {
		String drafter = insertEmployee(false, "재직");
		String approver = insertEmployee(false, "재직");
		String retired = insertEmployee(false, "퇴직");
		String administrator = insertEmployee(true, "재직");
		int approvalNo = 0;
		try {
			approval_file_VO originalFile = new approval_file_VO();
			originalFile.setApname(UUID.randomUUID().toString());
			originalFile.setAfname("preserved.txt");
			approvalNo = approvalService.createApproval(draft(uniqueTitle("후보 서버 검증")), drafter,
					List.of(originalFile), List.of(approver.toLowerCase(Locale.ROOT)));
			assertThat(approvalService.getLines(approvalNo)).extracting(approval_line_VO::getApproval_target)
					.containsExactly(approver);
			List<String> options = approvalService.getAvailableApprovers(loginMapper.findByUsernum(drafter)).stream()
					.map(userVO::getUsernum).toList();
			assertThat(options).contains(approver).doesNotContain(drafter, retired, administrator, "admin");
			Map<String, List<Map<String, Object>>> before = businessRows();
			for (List<String> candidates : List.of(List.of(drafter), List.of(drafter.toLowerCase(Locale.ROOT)),
					List.of(retired), List.of(administrator), List.of("없는사번"),
					List.of(approver, approver), List.of(approver, approver.toLowerCase(Locale.ROOT)))) {
				String title = uniqueTitle("저장 금지");
				assertThatThrownBy(() -> approvalService.createApproval(draft(title), drafter, List.of(), candidates))
						.isInstanceOf(IllegalArgumentException.class);
				approvalVO replacement = draft(title);
				replacement.setApproval_no(approvalNo);
				approval_file_VO replacementFile = new approval_file_VO();
				replacementFile.setApname(UUID.randomUUID().toString());
				replacementFile.setAfname("must-not-replace.txt");
				assertThatThrownBy(() -> approvalService.modifyApproval(replacement, drafter,
						List.of(replacementFile), candidates)).isInstanceOf(IllegalArgumentException.class);
				assertThat(businessRows()).isEqualTo(before);
			}
			HttpClient author = authenticatedClient(drafter);
			assertThat(postForm(author, "/approval/write.do", Map.of("kind", "기안서", "approval_title",
					uniqueTitle("HTTP 본인 결재 차단"), "approval_content", "차단", "approval_target", drafter))
					.statusCode()).isEqualTo(400);
			assertThat(businessRows()).isEqualTo(before);
		} finally {
			if (approvalNo != 0) jdbcTemplate.update("delete from approval where approval_no = ?", approvalNo);
			for (String target : List.of(drafter, approver, retired, administrator)) {
				jdbcTemplate.update("delete from user where usernum = ?", target);
			}
		}
	}

	@Test
	@Order(30)
	void legacySelfApprovalLineCannotBeDecidedThroughServiceOrHttp() throws Exception {
		String author = insertEmployee(false, "재직");
		String other = insertEmployee(false, "재직");
		int approvalNo = createDraft("이전 자가결재 이력", author, List.of(other));
		try {
			jdbcTemplate.update("update approval_line set approval_target = ? where approval_no = ?", author, approvalNo);
			Map<String, List<Map<String, Object>>> before = businessRows();
			assertThat(approvalService.canApprove(approvalService.getApproval(approvalNo),
					approvalService.getLines(approvalNo), author)).isFalse();
			assertThatThrownBy(() -> approvalService.processApproval(approvalNo, author, "승인", "본인 승인"))
					.isInstanceOf(AccessDeniedException.class);
			assertThat(decide(authenticatedClient(author), approvalNo, "승인", "본인 승인").statusCode()).isEqualTo(403);
			assertThat(businessRows()).isEqualTo(before);
		} finally {
			jdbcTemplate.update("delete from approval where approval_no = ?", approvalNo);
			jdbcTemplate.update("delete from user where usernum in (?, ?)", author, other);
		}
	}

	@Test
	@Order(31)
	void referencedEmployeeDeletionIsAtomicAndRetirementPreservesEveryBusinessRecord() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		String author = insertEmployee(false, "재직");
		String approver = insertEmployee(false, "재직");
		String noticeAuthor = insertEmployee(true, "재직");
		String chatPartner = insertEmployee(false, "재직");
		String unused = insertEmployee(false, "재직");
		int pending = createDraft("삭제해도 보존할 대기 문서", author, List.of(approver));
		int completed = createDraft("삭제해도 보존할 완료 문서", author, List.of(approver));
		approvalService.processApproval(completed, approver, "승인", "보존할 의견");
		noticeVO notice = new noticeVO();
		notice.setNotice_title(uniqueTitle("삭제해도 보존할 공지"));
		notice.setNotice_content("보존할 공지 본문");
		int noticeNo = noticeService.createNotice(notice, new String[] {"100"}, loginMapper.findByUsernum(noticeAuthor));
		long room = chatService.openRoom(author, chatPartner);
		chatService.sendMessage(room, author, "퇴직 후에도 보존할 메시지");
		HttpClient unusedSession = authenticatedClient(unused);
		try {
			Map<String, List<Map<String, Object>>> before = businessRows();
			assertThat(jdbcTemplate.queryForList("select DELETE_RULE from information_schema.REFERENTIAL_CONSTRAINTS "
					+ "where CONSTRAINT_SCHEMA = database() and REFERENCED_TABLE_NAME = 'user'", String.class))
					.hasSize(6).allMatch(rule -> rule.equals("RESTRICT") || rule.equals("NO ACTION"));
			for (String target : List.of(author, approver, noticeAuthor, chatPartner)) {
				assertThat(sendJson(admin, "DELETE", "/api/users/" + target, "{}").statusCode()).isEqualTo(409);
				assertThatThrownBy(() -> userMapper.deleteUser(target)).isInstanceOf(DataAccessException.class);
				assertThat(userService.getUser(target).getUsernum()).isEqualTo(target);
				assertThat(businessRows()).isEqualTo(before);
			}
			assertThat(sendJson(admin, "POST", "/api/users/bulk-delete", "[\"" + unused + "\",\"" + author + "\"]")
					.statusCode()).isEqualTo(409);
			assertThat(userService.getUser(unused).getUsernum()).isEqualTo(unused);
			assertThat(get(unusedSession, "/api/session").body()).contains("\"authenticated\":true");
			assertThat(businessRows()).isEqualTo(before);
			for (String target : List.of(author, approver, noticeAuthor)) {
				assertThat(sendJson(admin, "PUT", "/api/users/" + target, employeeUpdate(false, "퇴직")).statusCode())
						.isEqualTo(200);
				assertThat(businessRows()).isEqualTo(before);
			}
			assertThat(approvalService.getApproval(completed).getDocument_status()).isEqualTo("승인");
			assertThat(approvalService.getLines(pending)).singleElement()
					.extracting(approval_line_VO::getApproval_target).isEqualTo(approver);
			assertThat(get(authenticatedClient(chatPartner), "/api/chat/rooms/" + room + "/messages").body())
					.contains("퇴직 후에도 보존할 메시지");
		} finally {
			jdbcTemplate.update("delete from chat_room where room_no = ?", room);
			jdbcTemplate.update("delete from notice where notice_no = ?", noticeNo);
			jdbcTemplate.update("delete from approval where approval_no in (?, ?)", pending, completed);
			for (String target : List.of(author, approver, noticeAuthor, chatPartner, unused)) {
				jdbcTemplate.update("delete from user where usernum = ?", target);
			}
		}
	}

	@Test
	@Order(32)
	void concurrentDocumentCreationPreventsDeletingItsAuthorWithoutLosingTheNewHistory() throws Exception {
		String author = insertEmployee(false, "재직");
		String approver = insertEmployee(false, "재직");
		HttpClient admin = authenticatedClient("admin");
		HttpClient authorSession = authenticatedClient(author);
		CountDownLatch documentInserted = new CountDownLatch(1);
		CountDownLatch deletionStarted = new CountDownLatch(1);
		CountDownLatch commitDocument = new CountDownLatch(1);
		AtomicReference<Integer> createdNo = new AtomicReference<>();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> creation = executor.submit(() -> new TransactionTemplate(transactions).execute(transaction -> {
				int approvalNo = createDraft("사원 삭제와 작성 경합", author, List.of(approver));
				createdNo.set(approvalNo);
				documentInserted.countDown();
				try {
					assertThat(commitDocument.await(10, TimeUnit.SECONDS)).as("문서 commit 신호").isTrue();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
				return approvalNo;
			}));
			assertThat(documentInserted.await(10, TimeUnit.SECONDS)).isTrue();
			Future<HttpResponse<String>> deletion = executor.submit(() -> {
				deletionStarted.countDown();
				return sendJson(admin, "DELETE", "/api/users/" + author, "{}");
			});
			assertThat(deletionStarted.await(10, TimeUnit.SECONDS)).isTrue();
			commitDocument.countDown();
			int approvalNo = creation.get(20, TimeUnit.SECONDS);
			assertThat(deletion.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
			assertThat(approvalService.getApproval(approvalNo).getUsernum()).isEqualTo(author);
			assertThat(approvalService.getLines(approvalNo)).singleElement()
					.extracting(approval_line_VO::getApproval_target).isEqualTo(approver);
			assertThat(get(authorSession, "/api/session").body()).contains("\"authenticated\":true");
		} finally {
			commitDocument.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
			if (createdNo.get() != null) jdbcTemplate.update("delete from approval where approval_no = ?", createdNo.get());
			jdbcTemplate.update("delete from user where usernum in (?, ?)", author, approver);
		}
	}

	@Test
	@Order(33)
	void adminChecksUseCurrentRowsEvenWhenTheOuterTransactionHasAnOlderSnapshot() throws Exception {
		String target = insertEmployee(true, "재직");
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
				userVO staleActor = userMapper.selectUser(target);
				assertThat(staleActor.isAuthority()).isTrue();
				userVO registration = loginMapper.findByUsernum(target);
				registration.setUsernum("STALE-" + UUID.randomUUID());
				registration.setUserpw("1234");
				try {
					executor.submit(() -> {
						userVO demoted = userService.getUser(target);
						demoted.setAuthority(false);
						userService.updateUser(demoted, loginMapper.findByUsernum("admin"));
					}).get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError("별도 트랜잭션의 관리자 강등 commit", exception);
				}
				assertThatThrownBy(() -> userService.createUser(registration, staleActor))
						.isInstanceOf(AccessDeniedException.class);
				noticeVO notice = new noticeVO();
				notice.setNotice_title(uniqueTitle("이전 snapshot 관리자"));
				notice.setNotice_content("저장 금지");
				assertThatThrownBy(() -> noticeService.createNotice(notice, new String[] {"100"}, staleActor))
						.isInstanceOf(AccessDeniedException.class);
				transaction.setRollbackOnly();
			});
			assertThat(userService.getUser(target).isAuthority()).isFalse();
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
			jdbcTemplate.update("delete from user where usernum = ?", target);
		}
	}

	@Test
	@Order(34)
	void reloggingIntoAnotherAccountSurvivesPreviousAccountsRevocationAndCapturedSnapshot() throws Exception {
		for (boolean loginAfterSnapshot : List.of(false, true)) {
			String previousUser = insertEmployee(true, "재직");
			String nextUser = insertEmployee(false, "재직");
			try {
				HttpClient switched = authenticatedClient(previousUser);
				HttpClient previousSession = authenticatedClient(previousUser);
				userVO changed = userService.getUser(previousUser);
				if (loginAfterSnapshot) {
					changed.setUser_status("퇴직");
					new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
						userService.updateUser(changed, loginMapper.findByUsernum("admin"));
						try {
							assertThat(login(switched, nextUser, "1234").statusCode()).isEqualTo(200);
						} catch (Exception exception) {
							throw new AssertionError("이전 계정 회수 목록 생성 후 새 계정 로그인", exception);
						}
					});
				} else {
					assertThat(login(switched, nextUser, "1234").statusCode()).isEqualTo(200);
					changed.setAuthority(false);
					userService.updateUser(changed, loginMapper.findByUsernum("admin"));
				}
				assertThat(get(previousSession, "/api/session").body()).contains("\"authenticated\":false");
				assertThat(get(switched, "/api/session").body())
						.contains("\"authenticated\":true", "\"usernum\":\"" + nextUser + "\"", "\"authority\":false");
				assertThat(get(switched, "/api/users/me").body()).contains("\"usernum\":\"" + nextUser + "\"");
				assertThat(get(switched, "/api/users").statusCode()).isEqualTo(403);
			} finally {
				jdbcTemplate.update("delete from user where usernum in (?, ?)", previousUser, nextUser);
			}
		}
	}

	@Test
	@Order(35)
	void existingDatabaseHistoryMigrationPreservesRowsAndCanBeAppliedAgain() throws Exception {
		Map<String, List<Map<String, Object>>> before = businessRows();
		List<Map<String, Object>> usersBefore = jdbcTemplate.queryForList("select * from user order by usernum");
		try {
			setUserDeletionRule("CASCADE");
			assertThat(jdbcTemplate.queryForList("select DELETE_RULE from information_schema.REFERENTIAL_CONSTRAINTS "
					+ "where CONSTRAINT_SCHEMA = database() and REFERENCED_TABLE_NAME = 'user'", String.class))
					.contains("CASCADE");
			for (int application = 0; application < 2; application++) {
				try (var connection = jdbcTemplate.getDataSource().getConnection()) {
					ScriptUtils.executeSqlScript(connection, new FileSystemResource("document/migrations/20261021-preserve-user-history.sql"));
				}
				assertThat(jdbcTemplate.queryForList("select DELETE_RULE from information_schema.REFERENTIAL_CONSTRAINTS "
						+ "where CONSTRAINT_SCHEMA = database() and REFERENCED_TABLE_NAME = 'user'", String.class))
						.hasSize(6).containsOnly("RESTRICT");
				assertThat(businessRows()).isEqualTo(before);
				assertThat(jdbcTemplate.queryForList("select * from user order by usernum")).isEqualTo(usersBefore);
			}
		} finally {
			setUserDeletionRule("RESTRICT");
		}
	}

	@Test
	@Order(36)
	void attachmentReplacementDeletesOldBytesOnlyAfterCommitAndFailedUploadsAreCleaned() throws Exception {
		HttpClient author = authenticatedClient("2005004");
		String title = uniqueTitle("첨부 교체 정합성");
		Map<String, String> form = new LinkedHashMap<>(Map.of("kind", "기안서", "approval_code", "FILES",
				"approval_title", title, "approval_content", "첨부 본문", "approval_target", "2005002"));
		Set<String> before = uploadNames();
		HttpResponse<String> created = multipart(author, "/approval/write.do", form, "attach", "original.txt", "원본 첨부");
		assertThat(created.statusCode()).isEqualTo(302);
		int approvalNo = jdbcTemplate.queryForObject("select approval_no from approval where approval_title=?", Integer.class, title);
		Path original = Path.of(uploadDirectory, approvalService.getFiles(approvalNo).getFirst().getApname());
		assertThat(Files.readString(original)).isEqualTo("원본 첨부");
		form.put("approval_no", String.valueOf(approvalNo));
		Set<String> withOriginal = uploadNames();
		try {
			form.put("approval_target", "없는사번");
			assertThat(multipart(author, "/approval/modify.do", form, "attach", "failed.txt", "실패 파일").statusCode()).isEqualTo(400);
			assertThat(uploadNames()).isEqualTo(withOriginal);
			assertThat(Files.readString(original)).isEqualTo("원본 첨부");
			assertThat(approvalService.getFiles(approvalNo)).singleElement()
					.extracting(approval_file_VO::getApname).isEqualTo(original.getFileName().toString());

			form.put("approval_target", "2005002");
			assertThat(multipart(author, "/approval/modify.do", form, "attach", "new.txt", "새 첨부").statusCode()).isEqualTo(302);
			Path replacement = Path.of(uploadDirectory, approvalService.getFiles(approvalNo).getFirst().getApname());
			assertThat(original).doesNotExist();
			assertThat(Files.readString(replacement)).isEqualTo("새 첨부");
			assertThat(get(author, "/approval/down.do?no=" + approvalNo).body()).isEqualTo("새 첨부");

			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				approvalService.deleteApproval(approvalNo, "2005004");
				assertThat(replacement).exists();
				status.setRollbackOnly();
			});
			assertThat(approvalService.getApproval(approvalNo)).isNotNull();
			assertThat(replacement).exists();
			assertThat(postForm(author, "/approval/delete.do", Map.of("approval_no", String.valueOf(approvalNo))).statusCode()).isEqualTo(302);
			assertThat(replacement).doesNotExist();
			assertThat(uploadNames()).isEqualTo(before);
		} finally {
			jdbcTemplate.update("delete from approval where approval_no=?", approvalNo);
			for (String name : uploadNames()) if (!before.contains(name)) Files.deleteIfExists(Path.of(uploadDirectory, name));
		}
	}

	@Test
	@Order(37)
	void attachmentReplacementRollbackRetainsOriginalFileAndMetadata() throws Exception {
		Path original = Files.createDirectories(Path.of(uploadDirectory)).resolve(UUID.randomUUID().toString());
		Path replacement = original.resolveSibling(UUID.randomUUID().toString());
		Files.writeString(original, "원본");
		Files.writeString(replacement, "교체 시도");
		approval_file_VO oldFile = fileMetadata(original, "old.txt");
		approval_file_VO newFile = fileMetadata(replacement, "new.txt");
		int approvalNo = approvalService.createApproval(draft(uniqueTitle("파일 롤백")), "2005004", List.of(oldFile), List.of("2005002"));
		try {
			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				approvalVO change = draft(uniqueTitle("롤백될 수정"));
				change.setApproval_no(approvalNo);
				approvalService.modifyApproval(change, "2005004", List.of(newFile), List.of("2005002"));
				assertThat(original).exists();
				status.setRollbackOnly();
			});
			assertThat(Files.readString(original)).isEqualTo("원본");
			assertThat(approvalService.getFiles(approvalNo)).singleElement()
					.extracting(approval_file_VO::getApname).isEqualTo(original.getFileName().toString());
		} finally {
			jdbcTemplate.update("delete from approval where approval_no=?", approvalNo);
			Files.deleteIfExists(original);
			Files.deleteIfExists(replacement);
		}
	}

	@Test
	@Order(38)
	void noticeFileBindingCannotReferenceOrDeleteExistingFilesAndDeletionFollowsCommit() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		String title = uniqueTitle("공지 파일 바인딩");
		Set<String> before = uploadNames();
		Map<String, String> fields = new LinkedHashMap<>(Map.of("notice_title", title,
				"notice_content", "파일 원문", "notice_team", "100"));
		assertThat(multipart(admin, "/notice/writeOK.do", fields, "fileInput", "notice.txt", "공지 첨부 원문").statusCode()).isEqualTo(302);
		int originalNo = jdbcTemplate.queryForObject("select notice_no from notice where notice_title=?", Integer.class, title);
		noticeVO originalNotice = noticeService.getNotice(originalNo);
		Path original = Path.of(uploadDirectory, originalNotice.getPname());
		String forgedTitle = uniqueTitle("위조 파일명");
		try {
			Map<String, String> forged = new LinkedHashMap<>(Map.of("notice_title", forgedTitle,
					"notice_content", "위조", "pname", originalNotice.getPname(), "fname", "forged.txt"));
			assertThat(postForm(admin, "/notice/writeOK.do", forged).statusCode()).isEqualTo(400);
			assertThat(Files.readString(original)).isEqualTo("공지 첨부 원문");
			forged.put("notice_team", "100");
			assertThat(postForm(admin, "/notice/writeOK.do", forged).statusCode()).isEqualTo(302);
			noticeVO created = noticeService.getNotice(jdbcTemplate.queryForObject(
					"select notice_no from notice where notice_title=?", Integer.class, forgedTitle));
			assertThat(created.getPname()).isNull();
			assertThat(created.getFname()).isNull();
			assertThat(get(admin, "/notice/down.do?notice_no=" + originalNo).body()).isEqualTo("공지 첨부 원문");

			Set<String> existing = uploadNames();
			assertThat(multipart(admin, "/notice/writeOK.do", Map.of("notice_title", uniqueTitle("실패 공지"),
					"notice_content", "대상 없음"), "fileInput", "discard.txt", "폐기 파일").statusCode()).isEqualTo(400);
			assertThat(uploadNames()).isEqualTo(existing);
			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				noticeService.deleteNotice(originalNo, loginMapper.findByUsernum("admin"));
				assertThat(original).exists();
				status.setRollbackOnly();
			});
			assertThat(noticeService.getNotice(originalNo).getPname()).isEqualTo(originalNotice.getPname());
			assertThat(original).exists();
			assertThat(postForm(admin, "/notice/delete.do", Map.of("notice_no", String.valueOf(originalNo))).statusCode()).isEqualTo(302);
			assertThat(original).doesNotExist();
		} finally {
			jdbcTemplate.update("delete from notice where notice_title in (?,?)", title, forgedTitle);
			for (String name : uploadNames()) if (!before.contains(name)) Files.deleteIfExists(Path.of(uploadDirectory, name));
		}
	}

	@Test
	@Order(39)
	void blankAdminPasswordEditsPreserveHashAndLoginWhileRealChangesAreEncoded() throws Exception {
		String employee = insertEmployee(false, "재직");
		HttpClient admin = authenticatedClient("admin");
		authenticatedClient(employee);
		String initialHash = loginMapper.findByUsernum(employee).getUserpw();
		try {
			for (String value : List.of("null", "\"\"", "\"   \"", "\"\\t\\n\"")) {
				String update = employeeUpdate(false, "재직").replace("\"userpw\":\"\"", "\"userpw\":" + value);
				assertThat(sendJson(admin, "PUT", "/api/users/" + employee, update).statusCode()).isEqualTo(200);
				assertThat(loginMapper.findByUsernum(employee).getUserpw()).isEqualTo(initialHash);
				assertThat(login(newClient(), employee, "1234").statusCode()).isEqualTo(200);
			}
			String changed = employeeUpdate(false, "재직").replace("\"userpw\":\"\"", "\"userpw\":\"Changed-4567!\"");
			assertThat(sendJson(admin, "PUT", "/api/users/" + employee, changed).statusCode()).isEqualTo(200);
			assertThat(loginMapper.findByUsernum(employee).getUserpw()).startsWith("{bcrypt}").isNotEqualTo(initialHash);
			assertThat(login(newClient(), employee, "1234").statusCode()).isEqualTo(401);
			assertThat(login(newClient(), employee, "Changed-4567!").statusCode()).isEqualTo(200);
		} finally {
			userService.deleteUser(employee, loginMapper.findByUsernum("admin"));
		}
	}

	@Test
	@Order(40)
	void completedApprovalFilterUsesSameCountAndRowsOnEveryPage() throws Exception {
		String development = insertEmployee(false, "재직");
		String design = insertEmployee(false, "재직");
		jdbcTemplate.update("update user set team='디자인' where usernum=?", design);
		String prefix = uniqueTitle("부서별 완료 건수");
		List<Integer> documents = new ArrayList<>();
		try {
			for (int index = 0; index < 12; index++) {
				int number = createDraft(prefix, index < 11 ? development : design, List.of("2005002"));
				documents.add(number);
				approvalService.processApproval(number, "2005002", "승인", "완료");
			}
			HttpClient admin = authenticatedClient("admin");
			String query = "/api/approvals/completed?keyword=" + encode(prefix) + "&team=";
			JsonNode first = json.readTree(get(admin, query + encode("개발") + "&page=1").body());
			JsonNode second = json.readTree(get(admin, query + encode("개발") + "&page=2").body());
			assertThat(first.get("totalCount").asInt()).isEqualTo(11);
			assertThat(first.get("totalPages").asInt()).isEqualTo(2);
			assertThat(first.get("items").size()).isEqualTo(10);
			assertThat(second.get("totalCount").asInt()).isEqualTo(11);
			assertThat(second.get("items").size()).isEqualTo(1);
			assertThat(first.get("items").findValuesAsString("team")).containsOnly("개발");
			JsonNode other = json.readTree(get(admin, query + encode("디자인")).body());
			assertThat(other.get("totalCount").asInt()).isEqualTo(1);
			assertThat(other.get("items").size()).isEqualTo(1);
			assertThat(json.readTree(get(admin, query + encode("없는부서")).body()).get("totalCount").asInt()).isZero();
		} finally {
			for (int number : documents) jdbcTemplate.update("delete from approval where approval_no=?", number);
			jdbcTemplate.update("delete from user where usernum in (?,?)", development, design);
		}
	}

	@Test
	@Order(41)
	void excessivePageOffsetsAreRejectedConsistentlyWithoutSqlErrors() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		for (String path : List.of("/api/users", "/api/notices", "/api/approvals", "/api/approvals/received", "/api/approvals/completed")) {
			assertThat(get(admin, path + "?page=2147483647").statusCode()).as(path).isEqualTo(400);
			HttpResponse<String> first = get(admin, path + "?page=0");
			assertThat(first.statusCode()).as(path).isEqualTo(200);
			assertThat(json.readTree(first.body()).get("page").asInt()).isEqualTo(1);
			assertThat(get(admin, path + "?page=100000").statusCode()).as(path).isEqualTo(200);
		}
	}

	@Test
	@Order(42)
	void readBoundaryPreservesNewerAndOtherRoomMessagesAndIsIdempotent() throws Exception {
		String sender = insertEmployee(false, "재직");
		String reader = insertEmployee(false, "재직");
		long room = chatService.openRoom(sender, reader);
		long otherRoom = chatService.openRoom(sender, "2005002");
		try {
			long visible = chatService.sendMessage(room, sender, "화면에 표시됨").getMessageNo();
			long newer = chatService.sendMessage(room, sender, "나중에 도착할 메시지").getMessageNo();
			long own = chatService.sendMessage(room, reader, "수신자 자신의 메시지").getMessageNo();
			long other = chatService.sendMessage(otherRoom, sender, "다른 방 메시지").getMessageNo();
			HttpClient recipient = authenticatedClient(reader);
			String readPath = "/api/chat/rooms/" + room + "/read?throughMessageNo=";
			assertThat(sendJson(recipient, "POST", readPath + visible, "{}").body()).contains("\"updated\":1");
			assertThat(jdbcTemplate.queryForObject("select read_at is not null from chat_message where message_no=?", Boolean.class, visible)).isTrue();
			for (long unread : List.of(newer, own, other)) {
				assertThat(jdbcTemplate.queryForObject("select read_at is null from chat_message where message_no=?", Boolean.class, unread)).isTrue();
			}
			assertThat(sendJson(recipient, "POST", readPath + visible, "{}").body()).contains("\"updated\":0");
			assertThat(sendJson(recipient, "POST", readPath + "0", "{}").statusCode()).isEqualTo(400);
			assertThat(sendJson(recipient, "POST", readPath + other, "{}").statusCode()).isEqualTo(400);
			assertThat(sendJson(authenticatedClient("2005007"), "POST", readPath + visible, "{}").statusCode()).isEqualTo(403);
			assertThat(sendJson(recipient, "POST", "/api/chat/rooms/" + room + "/read", "{}").body()).contains("\"updated\":1");
		} finally {
			jdbcTemplate.update("delete from chat_room where room_no in (?,?)", room, otherRoom);
			jdbcTemplate.update("delete from user where usernum in (?,?)", sender, reader);
		}
	}

	@Test
	@Order(43)
	void deletingOneDocumentRetainsAFileStillReferencedByAnotherBusinessRecord() throws Exception {
		Path stored = Files.createDirectories(Path.of(uploadDirectory)).resolve(UUID.randomUUID().toString());
		Files.writeString(stored, "공유된 기존 첨부");
		int approvalNo = approvalService.createApproval(draft(uniqueTitle("기존 공유 파일")), "2005004",
				List.of(fileMetadata(stored, "shared.txt")), List.of("2005002"));
		noticeVO notice = new noticeVO();
		notice.setNotice_title(uniqueTitle("기존 공유 공지"));
		notice.setNotice_content("보존 확인");
		notice.setPname(stored.getFileName().toString());
		notice.setFname("shared.txt");
		int noticeNo = noticeService.createNotice(notice, new String[] {"100"}, loginMapper.findByUsernum("admin"));
		try {
			approvalService.deleteApproval(approvalNo, "2005004");
			assertThat(Files.readString(stored)).isEqualTo("공유된 기존 첨부");
			noticeService.deleteNotice(noticeNo, loginMapper.findByUsernum("admin"));
			assertThat(stored).doesNotExist();
		} finally {
			jdbcTemplate.update("delete from notice where notice_no=?", noticeNo);
			jdbcTemplate.update("delete from approval where approval_no=?", approvalNo);
			Files.deleteIfExists(stored);
		}
	}

	@Test
	@Order(44)
	void equalDepartmentAndLevelEmployeesHaveStableUniqueOrderingAcrossPages() throws Exception {
		String name = "정렬" + UUID.randomUUID().toString().substring(0, 8);
		List<String> employees = new ArrayList<>();
		try {
			for (int index = 0; index < 12; index++) {
				String employee = insertEmployee(false, "재직");
				employees.add(employee);
				jdbcTemplate.update("update user set name=? where usernum=?", name, employee);
			}
			List<String> expected = employees.stream().sorted().toList();
			HttpClient admin = authenticatedClient("admin");
			String query = "/api/users?team=" + encode("개발") + "&searchKey=name&searchWord=" + encode(name);
			for (int repeat = 0; repeat < 2; repeat++) {
				JsonNode first = json.readTree(get(admin, query + "&page=1").body());
				JsonNode second = json.readTree(get(admin, query + "&page=2").body());
				assertThat(first.get("totalCount").asInt()).isEqualTo(12);
				List<String> actual = new ArrayList<>(first.get("items").findValuesAsString("usernum"));
				actual.addAll(second.get("items").findValuesAsString("usernum"));
				assertThat(actual).containsExactlyElementsOf(expected);
			}
		} finally {
			for (String employee : employees) jdbcTemplate.update("delete from user where usernum=?", employee);
		}
	}

	private Set<String> uploadNames() throws IOException {
		Path root = Files.createDirectories(Path.of(uploadDirectory));
		try (var files = Files.list(root)) {
			return files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
		}
	}

	private static approval_file_VO fileMetadata(Path path, String original) {
		approval_file_VO file = new approval_file_VO();
		file.setApname(path.getFileName().toString());
		file.setAfname(original);
		return file;
	}

	private HttpResponse<String> multipart(HttpClient http, String path, Map<String, String> fields,
			String fileField, String originalName, String content) throws Exception {
		Matcher csrf = csrf(http);
		String boundary = "erp-" + UUID.randomUUID();
		StringBuilder body = new StringBuilder();
		fields.forEach((name, value) -> body.append("--").append(boundary)
				.append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
				.append(value).append("\r\n"));
		body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
				.append(fileField).append("\"; filename=\"").append(originalName)
				.append("\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n").append(content)
				.append("\r\n--").append(boundary).append("--\r\n");
		return http.send(HttpRequest.newBuilder(uri(path)).header(csrf.group(2), csrf.group(3))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private void setUserDeletionRule(String rule) {
		for (String[] foreignKey : List.of(new String[] {"notice", "fk_notice_user", "usernum"},
				new String[] {"approval", "fk_approval_user", "usernum"},
				new String[] {"approval_line", "fk_approval_line_user", "approval_target"},
				new String[] {"chat_room", "fk_chat_room_user_a", "user_a"},
				new String[] {"chat_room", "fk_chat_room_user_b", "user_b"},
				new String[] {"chat_message", "fk_chat_message_sender", "sender_usernum"})) {
			String deletionRule = "CASCADE".equals(rule) && "notice".equals(foreignKey[0]) ? "NO ACTION" : rule;
			// Dedicated test database; no application writes run during this fixture-only DDL.
			jdbcTemplate.execute("alter table " + foreignKey[0] + " drop foreign key " + foreignKey[1]);
			jdbcTemplate.execute("alter table " + foreignKey[0] + " add constraint " + foreignKey[1] + " foreign key (" + foreignKey[2]
					+ ") references user(usernum) on delete " + deletionRule);
		}
	}

	private String insertEmployee(boolean authority, String status) {
		String usernum = "P1-" + UUID.randomUUID().toString().replace("-", "");
		jdbcTemplate.update("insert into user (usernum, userpw, name, idnum1, idnum2, phonenum, officenum, "
				+ "email, team, level, firstlogin, authority, level_num, user_status) "
				+ "values (?, MD5('1234'), '회귀 사원', '990101', '1', '010-1111-2222', '02-111-2222', "
				+ "'regression@example.com', '개발', '사원', false, ?, 400, ?)", usernum, authority, status);
		return usernum;
	}

	private static String employeeUpdate(boolean authority, String status) {
		return """
				{"userpw":"","name":"회귀 사원","idnum1":"990101","idnum2":"1",
				 "phonenum":"010-1111-2222","officenum":"02-111-2222","email":"regression@example.com",
				 "team":"개발","level":"사원","joindate":"2026-10-21","authority":%s,"userStatus":"%s"}
				""".formatted(authority, status);
	}

	private Map<String, List<Map<String, Object>>> businessRows() {
		Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
		for (String table : List.of("approval", "approval_line", "approval_file", "notice", "notice_team", "chat_room", "chat_message")) {
			rows.put(table, jdbcTemplate.queryForList("select * from " + table + " order by 1, 2"));
		}
		return rows;
	}

	private static String userNumber(String json) {
		Matcher number = Pattern.compile("\"usernum\":\"([^\"]+)\"").matcher(json);
		assertThat(number.find()).as("등록 응답의 사원번호").isTrue();
		return number.group(1);
	}

	private int createDraft(String titlePrefix, String drafter, List<String> approvers) {
		return approvalService.createApproval(
				draft(uniqueTitle(titlePrefix)), drafter, List.of(), approvers);
	}

	private static approvalVO draft(String title) {
		approvalVO draft = new approvalVO();
		draft.setKind("기안서");
		draft.setApproval_code(UUID.randomUUID().toString());
		draft.setApproval_title(title);
		draft.setApproval_content("통합 테스트 결재 내용");
		return draft;
	}

	private int insertNotice(String title, String team) {
		jdbcTemplate.update("insert into notice "
				+ "(notice_title, notice_content, is_important, is_main, usernum) "
				+ "values (?, '통합 테스트 공지 내용', false, false, 'admin')", title);
		Integer noticeNo = jdbcTemplate.queryForObject(
				"select notice_no from notice where notice_title = ?", Integer.class, title);
		jdbcTemplate.update("insert into notice_team (notice_no, notice_team) values (?, ?)",
				noticeNo, team);
		return noticeNo;
	}

	private HttpClient authenticatedClient(String usernum) throws Exception {
		HttpClient authenticated = newClient();
		assertThat(login(authenticated, usernum, "1234").statusCode()).isEqualTo(200);
		return authenticated;
	}

	private HttpResponse<String> decide(HttpClient http, int approvalNo,
			String status, String comment) throws Exception {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("approval_no", String.valueOf(approvalNo));
		form.put("approval_status", status);
		form.put("comment", comment);
		return postForm(http, "/approval/updateComment.do",
				form);
	}

	private HttpResponse<String> login(HttpClient http, String usernum, String password) throws Exception {
		return postForm(http, "/login/login.do",
				Map.of("usernum", usernum, "userpw", password));
	}

	private HttpResponse<String> postForm(HttpClient http, String path,
			Map<String, String> values) throws Exception {
		Matcher csrf = csrf(http);

		StringBuilder form = new StringBuilder(csrf.group(1))
				.append('=').append(encode(csrf.group(3)));
		values.forEach((name, value) -> form.append('&').append(encode(name))
				.append('=').append(encode(value)));
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form.toString()))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> sendJson(HttpClient http, String method, String path,
			String body) throws Exception {
		Matcher csrf = csrf(http);
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.header(csrf.group(2), csrf.group(3))
				.method(method, HttpRequest.BodyPublishers.ofString(body))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private Matcher csrf(HttpClient http) throws Exception {
		Matcher csrf = CSRF_JSON.matcher(get(http, "/api/session").body());
		assertThat(csrf.find()).as("CSRF token from /api/session").isTrue();
		return csrf;
	}

	private HttpResponse<String> get(HttpClient http, String path) throws Exception {
		return http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpClient newClient() {
		CookieManager cookies = new CookieManager();
		cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		return HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + "/ERP" + path);
	}

	private static String uniqueTitle(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String md5(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
