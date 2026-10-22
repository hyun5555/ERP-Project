package com.erp.config;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import com.erp.service.LoginService;
import com.erp.service.SessionRevocationService;
import com.erp.vo.userVO;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	@SuppressWarnings("deprecation")
	PasswordEncoder passwordEncoder() {
		DelegatingPasswordEncoder encoder =
				(DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
		encoder.setDefaultPasswordEncoderForMatches(new MessageDigestPasswordEncoder("MD5"));
		return encoder;
	}

	@Bean
	ServletListenerRegistrationBean<SessionRevocationService> sessionRevocationListener(
			SessionRevocationService sessions) {
		return new ServletListenerRegistrationBean<>(sessions);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			PasswordEncoder passwordEncoder, LoginService loginService,
			SessionRevocationService sessions) throws Exception {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(loginService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		authenticationProvider.setUserDetailsPasswordService(loginService);

		http
			.authenticationProvider(authenticationProvider)
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers("/", "/login/login.do", "/api/session", "/resources/**", "/error").permitAll()
				.requestMatchers("/user/myinfo.do", "/api/users/me", "/api/users/me/password")
					.authenticated()
				.requestMatchers("/user/**", "/api/users/**").hasRole("ADMIN")
				.requestMatchers("/notice/write.do", "/notice/writeOK.do", "/notice/delete.do")
					.hasRole("ADMIN")
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, denied) -> {
				if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/")
						&& request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
					response.sendRedirect(request.getContextPath() + "/login/login.do");
				} else {
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
				}
			}))
			.formLogin(form -> form
				.loginPage("/login/login.do")
				.loginProcessingUrl("/login/login.do")
				.usernameParameter("usernum")
				.passwordParameter("userpw")
				.successHandler((request, response, authentication) -> {
					userVO loginUser;
					try {
						loginUser = sessions.register(request.getSession(), authentication);
					} catch (org.springframework.security.core.AuthenticationException | IllegalStateException changed) {
						SecurityContextHolder.clearContext();
						write(response, HttpServletResponse.SC_UNAUTHORIZED, "ERROR");
						return;
					}
					write(response, HttpServletResponse.SC_OK,
							loginUser.isFirstlogin() ? "CHANGE" : "OK");
				})
				.failureHandler((request, response, exception) ->
					write(response, HttpServletResponse.SC_UNAUTHORIZED, "ERROR"))
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/login/logout.do")
				.logoutSuccessUrl("/login/login.do")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));

		return http.build();
	}

	private static void write(HttpServletResponse response, int status, String body) throws IOException {
		response.setStatus(status);
		response.setContentType("text/plain;charset=UTF-8");
		response.getWriter().write(body);
	}
}
