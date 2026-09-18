package com.erp.control;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

	@GetMapping("/")
	public String login() {
		return "app";
	}

	@GetMapping("/login/login.do")
	public String showLogin() {
		return "login/login";
	}

	@GetMapping("/login/changepw.do")
	public String changePasswordPage() {
		return "app";
	}
}
