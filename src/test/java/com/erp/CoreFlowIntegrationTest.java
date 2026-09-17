package com.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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

import com.erp.mapper.LoginMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class CoreFlowIntegrationTest {

	private static final Pattern CSRF_INPUT = Pattern.compile(
			"type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	@Autowired
	private LoginMapper loginMapper;

	private HttpClient client;

	@BeforeEach
	void createClient() {
		CookieManager cookies = new CookieManager();
		cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Test
	@Order(1)
	void loginPageIsAccessibleWithoutAuthentication() throws Exception {
		HttpResponse<String> response = get("/login/login.do");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("사원번호", "_csrf");
	}

	@Test
	@Order(2)
	void protectedPageRedirectsToLogin() throws Exception {
		HttpResponse<String> response = get("/main.do");

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("location").orElse(""))
				.endsWith("/ERP/login/login.do");
	}

	@Test
	@Order(3)
	void invalidCredentialsAreRejected() throws Exception {
		HttpResponse<String> response = login("admin", "wrong-password");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).isEqualTo("ERROR");
	}

	@Test
	@Order(4)
	void legacyPasswordIsUpgradedAndCorePagesOpen() throws Exception {
		loginMapper.updatePassword("admin", md5("1234"));

		HttpResponse<String> login = login("admin", "1234");
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).isEqualTo("OK");
		assertThat(loginMapper.findByUsernum("admin").getUserpw()).startsWith("{bcrypt}");

		assertThat(get("/main.do").statusCode()).isEqualTo(200);
		assertThat(get("/notice/list.do").statusCode()).isEqualTo(200);
		assertThat(get("/approval/list.do").statusCode()).isEqualTo(200);
	}

	private HttpResponse<String> login(String usernum, String password) throws Exception {
		HttpResponse<String> loginPage = get("/login/login.do");
		Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
		assertThat(csrf.find()).as("login page CSRF token").isTrue();

		String form = csrf.group(1) + "=" + encode(csrf.group(2))
				+ "&usernum=" + encode(usernum)
				+ "&userpw=" + encode(password);
		HttpRequest request = HttpRequest.newBuilder(uri("/login/login.do"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + "/ERP" + path);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String md5(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
