package com.erp.control;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

	@GetMapping("/")
	public String login() {
		return "forward:/resources/app/index.html";
	}

	@GetMapping("/login/login.do")
	public String showLogin() {
		return "forward:/resources/app/index.html";
	}

	@GetMapping("/login/changepw.do")
	public String changePasswordPage() {
		return "forward:/resources/app/index.html";
	}
}
