package com.erp.control;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erp.service.ApprovalService;
import com.erp.service.NoticeService;
import com.erp.vo.noticeVO;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api")
public class DashboardApiController {

	private final ApprovalService approvalService;
	private final NoticeService noticeService;

	public DashboardApiController(ApprovalService approvalService, NoticeService noticeService) {
		this.approvalService = approvalService;
		this.noticeService = noticeService;
	}

	@GetMapping("/session")
	public SessionResponse session(HttpSession session, HttpServletRequest request) {
		userVO user = (userVO) session.getAttribute("loginUser");
		CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
		return new SessionResponse(user != null, UserSummary.from(user),
				new CsrfInfo(token.getParameterName(), token.getHeaderName(), token.getToken()));
	}

	@GetMapping("/dashboard")
	public DashboardResponse dashboard(HttpSession session) {
		userVO user = (userVO) session.getAttribute("loginUser");
		List<NoticeItem> notices = noticeService.getMainNotices().stream()
				.map(NoticeItem::from)
				.toList();
		return new DashboardResponse(approvalService.getCounts(user.getUsernum()), notices);
	}

	public record CsrfInfo(String parameterName, String headerName, String token) {}

	public record SessionResponse(boolean authenticated, UserSummary user, CsrfInfo csrf) {}

	public record UserSummary(String usernum, String name, String team, String level,
			boolean authority) {
		static UserSummary from(userVO user) {
			return user == null ? null : new UserSummary(user.getUsernum(), user.getName(),
					user.getTeam(), user.getLevel(), user.isAuthority());
		}
	}

	public record DashboardResponse(Map<String, Integer> counts, List<NoticeItem> notices) {}

	public record NoticeItem(int noticeNo, String title, boolean important, String date) {
		static NoticeItem from(noticeVO notice) {
			return new NoticeItem(notice.getNotice_no(), notice.getNotice_title(),
					notice.isIs_important(), notice.getnoticedate());
		}
	}
}
