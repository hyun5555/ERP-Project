package com.erp.control;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.erp.service.LoginService;
import com.erp.vo.userVO;

@Controller
public class LoginController {

	private final LoginService loginService;

	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	@GetMapping("/")
	public String login() {
		return "redirect:/login/login.do";
	}

	@GetMapping("/login/login.do")
	public String showLogin() {
		return "login/login";
	}

	@GetMapping("/login/changepw.do")
	public String changePasswordPage() {
		return "login/changepw";
	}

	@PostMapping("/login/changepw.do")
	@ResponseBody
	public String changePassword(@RequestParam String oldpw,
			@RequestParam String newpw, HttpSession session) {
		userVO loginUser = (userVO) session.getAttribute("loginUser");
		if (loginUser == null || !loginService.changePassword(loginUser.getUsernum(), oldpw, newpw)) {
			return "ERROR";
		}

		session.setAttribute("loginUser", loginService.findByUsernum(loginUser.getUsernum()));
		return "OK";
	}
}
