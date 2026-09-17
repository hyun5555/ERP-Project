package com.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import com.erp.mapper.LoginMapper;
import com.erp.service.ApprovalService;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class CoreFlowIntegrationTest {

	private static final Pattern CSRF_INPUT = Pattern.compile(
			"type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]+)\"");

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
	private JdbcTemplate jdbcTemplate;

	private HttpClient client;

	@BeforeEach
	void createClient() {
		client = newClient();
	}

	@Test
	@Order(1)
	void loginPageIsAccessibleWithoutAuthentication() throws Exception {
		HttpResponse<String> landing = get(client, "/");
		HttpResponse<String> response = get(client, "/login/login.do");

		assertThat(landing.statusCode()).isEqualTo(200);
		assertThat(landing.body()).contains("id=\"root\"", "EZEN Works");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("사원번호", "_csrf");
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

		assertThat(decide(firstApprover, approvalNo, "승인", "1차 승인").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("진행중");

		assertThat(decide(secondApprover, approvalNo, "승인", "최종 승인").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("승인");
	}

	@Test
	@Order(7)
	void rejectionEndsDocumentAndRequiresComment() throws Exception {
		int approvalNo = createDraft("반려 처리", "2005004", List.of("2005002"));
		HttpClient approver = authenticatedClient("2005002");

		assertThat(postForm(approver, "/approval/updateComment.do", "/approval/view.do?approval_no=" + approvalNo,
				Map.of("approval_no", String.valueOf(approvalNo), "approval_status", "반려", "comment", ""))
				.statusCode()).isEqualTo(400);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("대기중");

		assertThat(decide(approver, approvalNo, "반려", "내용 보완 필요").statusCode()).isEqualTo(302);
		assertThat(approvalService.getApproval(approvalNo).getDocument_status()).isEqualTo("반려");
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
				.isInstanceOf(DataAccessException.class);
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

		HttpResponse<String> detail = get(admin, "/user/view.do?usernum=2005004");
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(detail.body()).doesNotContain("사원PW", "{bcrypt}");
		assertThat(get(admin, "/user/list.do").statusCode()).isEqualTo(200);
	}

	@Test
	@Order(12)
	void adminCanCreateModifyAndDeleteEmployee() throws Exception {
		HttpClient admin = authenticatedClient("admin");
		String usernum = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		Map<String, String> create = new LinkedHashMap<>();
		create.put("usernum", usernum);
		create.put("userpw", "Initial-1234!");
		create.put("name", "테스트사원");
		create.put("idnum1", "990101");
		create.put("idnum2", "1");
		create.put("phonenum", "010-1111-2222");
		create.put("officenum", "02-111-2222");
		create.put("email", "test@example.com");
		create.put("team", "개발");
		create.put("level", "사원");
		create.put("user_status", "재직");
		create.put("authority", "false");

		assertThat(postForm(admin, "/user/write.do", "/user/write.do", create).statusCode())
				.isEqualTo(302);
		Map<String, Object> inserted = jdbcTemplate.queryForMap(
				"select userpw, firstlogin, level_num from user where usernum = ?", usernum);
		assertThat(inserted.get("userpw").toString()).startsWith("{bcrypt}");
		assertThat(inserted.get("firstlogin")).isEqualTo(true);
		assertThat(inserted.get("level_num")).isEqualTo(400);

		Map<String, String> update = new LinkedHashMap<>();
		update.put("usernum", usernum);
		update.put("userpw", "");
		update.put("name", "수정사원");
		update.put("idnum1", "990101");
		update.put("idnum2", "1");
		update.put("phonenum1", "010");
		update.put("phonenum2", "3333");
		update.put("phonenum3", "4444");
		update.put("officenum1", "02");
		update.put("officenum2", "333");
		update.put("officenum3", "4444");
		update.put("email1", "updated");
		update.put("email2", "example.com");
		update.put("team", "개발");
		update.put("level", "대리");
		update.put("user_status", "재직");
		update.put("authority", "false");

		assertThat(postForm(admin, "/user/modify.do", "/user/modify.do?usernum=" + usernum,
				update).statusCode()).isEqualTo(302);
		assertThat(jdbcTemplate.queryForObject(
				"select concat(name, ':', level_num) from user where usernum = ?", String.class, usernum))
				.isEqualTo("수정사원:300");

		assertThat(postForm(admin, "/user/delete.do", "/user/view.do?usernum=" + usernum,
				Map.of("usernum", usernum)).statusCode()).isEqualTo(302);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from user where usernum = ?", Integer.class, usernum)).isZero();
	}

	@Test
	@Order(13)
	void noticeChangesAreAdminOnly() throws Exception {
		HttpClient employee = authenticatedClient("2005007");
		HttpClient admin = authenticatedClient("admin");

		assertThat(get(employee, "/notice/write.do").statusCode()).isEqualTo(403);
		assertThat(postForm(employee, "/notice/delete.do", "/notice/view.do?notice_no=1",
				Map.of("notice_no", "1")).statusCode()).isEqualTo(403);
		assertThat(get(employee, "/notice/view.do?notice_no=1").statusCode()).isEqualTo(200);
		assertThat(get(admin, "/notice/write.do").statusCode()).isEqualTo(200);

		String title = uniqueTitle("관리자 공지");
		assertThat(postForm(admin, "/notice/writeOK.do", "/notice/write.do", Map.of(
				"notice_title", title,
				"notice_content", "관리자 전용 공지",
				"notice_team", "100")).statusCode()).isEqualTo(302);
		Integer noticeNo = jdbcTemplate.queryForObject(
				"select notice_no from notice where notice_title = ?", Integer.class, title);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from notice_team where notice_no = ?", Integer.class, noticeNo))
				.isEqualTo(1);
		assertThat(postForm(admin, "/notice/delete.do", "/notice/view.do?notice_no=" + noticeNo,
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
		assertThat(postForm(other, "/approval/modify.do", "/main.do", Map.of(
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

		HttpResponse<String> rejected = postForm(employee, "/login/changepw.do",
				"/login/changepw.do", Map.of("oldpw", "wrong", "newpw", "Changed-1234!"));
		assertThat(rejected.statusCode()).isEqualTo(200);
		assertThat(rejected.body()).isEqualTo("ERROR");

		HttpResponse<String> changed = postForm(employee, "/login/changepw.do",
				"/login/changepw.do", Map.of("oldpw", "1234", "newpw", "Changed-1234!"));
		assertThat(changed.statusCode()).isEqualTo(200);
		assertThat(changed.body()).isEqualTo("OK");
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
				"/approval/view.do?approval_no=" + approvalNo, form);
	}

	private HttpResponse<String> login(HttpClient http, String usernum, String password) throws Exception {
		return postForm(http, "/login/login.do", "/login/login.do",
				Map.of("usernum", usernum, "userpw", password));
	}

	private HttpResponse<String> postForm(HttpClient http, String path,
			String csrfPage, Map<String, String> values) throws Exception {
		Matcher csrf = CSRF_INPUT.matcher(get(http, csrfPage).body());
		assertThat(csrf.find()).as("CSRF token at " + csrfPage).isTrue();

		StringBuilder form = new StringBuilder(csrf.group(1))
				.append('=').append(encode(csrf.group(2)));
		values.forEach((name, value) -> form.append('&').append(encode(name))
				.append('=').append(encode(value)));
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form.toString()))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
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
