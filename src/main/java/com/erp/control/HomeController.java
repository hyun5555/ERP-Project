package com.erp.control;

import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.erp.service.ApprovalService;
import com.erp.vo.userVO;

@Controller
public class HomeController {

	private final ApprovalService approvalService;

	public HomeController(ApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping("/main.do")
	public String main() {
		return "app";
	}

	@GetMapping("/approval/count.do")
	@ResponseBody
	public String approvalCount(HttpSession session) {
		userVO loginUser = (userVO) session.getAttribute("loginUser");
		Map<String, Integer> counts = approvalService.getCounts(loginUser.getUsernum());
		return counts.get("대기중") + "/" + counts.get("반려") + "/"
				+ counts.get("진행중") + "/" + counts.get("승인") + "/" + counts.get("수신");
	}
}
