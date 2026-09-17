package com.erp.config;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.erp.service.LoginService;
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
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			PasswordEncoder passwordEncoder, LoginService loginService) throws Exception {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(loginService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		authenticationProvider.setUserDetailsPasswordService(loginService);

		http
			.authenticationProvider(authenticationProvider)
			.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers("/", "/login/login.do", "/resources/**", "/error").permitAll()
				.requestMatchers("/user/myinfo.do").authenticated()
				.requestMatchers("/user/**").hasRole("ADMIN")
				.requestMatchers("/notice/write.do", "/notice/writeOK.do", "/notice/delete.do")
					.hasRole("ADMIN")
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login/login.do")
				.loginProcessingUrl("/login/login.do")
				.usernameParameter("usernum")
				.passwordParameter("userpw")
				.successHandler((request, response, authentication) -> {
					userVO loginUser = loginService.findByUsernum(authentication.getName());
					request.getSession().setAttribute("loginUser", loginUser);
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
