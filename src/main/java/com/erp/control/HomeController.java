package com.erp.control;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.erp.service.ApprovalService;
import com.erp.service.NoticeService;
import com.erp.vo.noticeVO;
import com.erp.vo.userVO;

@Controller
public class HomeController {

	private final NoticeService noticeService;
	private final ApprovalService approvalService;

	public HomeController(NoticeService noticeService, ApprovalService approvalService) {
		this.noticeService = noticeService;
		this.approvalService = approvalService;
	}

	@GetMapping("/main.do")
	public String main(Model model) {
		List<noticeVO> notices = noticeService.getMainNotices();
		model.addAttribute("list", notices);
		return "main";
	}

	@GetMapping("/approval/count.do")
	@ResponseBody
	public String approvalCount(HttpSession session) {
		userVO loginUser = (userVO) session.getAttribute("loginUser");
		Map<String, Object> params = new HashMap<>();
		params.put("kind", "");
		params.put("usernum", loginUser.getUsernum());
		params.put("keyword", "");
		params.put("offset", 0);
		params.put("limit", 0);

		StringBuilder counts = new StringBuilder();
		for (String status : List.of("대기중", "반려", "진행중", "승인")) {
			params.put("status", status);
			counts.append(approvalService.countDrafts(params)).append('/');
		}
		params.put("status", "");
		return counts.append(approvalService.countReceived(params)).toString();
	}
}
