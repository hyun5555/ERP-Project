package com.erp.control;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

	@GetMapping({
		"/user/list.do", "/user/view.do", "/user/modify.do",
		"/user/write.do", "/user/myinfo.do"
	})
	public String app() {
		return "app";
	}
}
