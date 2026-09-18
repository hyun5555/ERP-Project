package com.erp.control;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.erp.service.ApprovalService;
import com.erp.service.NoticeService;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.noticeVO;
import com.erp.vo.notice_teamVO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api")
public class FrontendApiController {

	private static final int PAGE_SIZE = 10;

	private final ApprovalService approvalService;
	private final NoticeService noticeService;

	public FrontendApiController(ApprovalService approvalService, NoticeService noticeService) {
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
		userVO user = loginUser(session);
		List<NoticeItem> notices = noticeService.getMainNotices().stream()
				.map(NoticeItem::from)
				.toList();
		return new DashboardResponse(approvalService.getCounts(user.getUsernum()), notices);
	}

	@GetMapping("/approvals")
	public ApprovalListResponse approvals(
			@RequestParam(defaultValue = "0") String mode,
			@RequestParam(defaultValue = "") String kind,
			@RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "1") int page,
			HttpSession session) {
		page = Math.max(page, 1);
		status = statusForMode(mode, status);
		Map<String, Object> params = searchParams(kind, status, keyword, page);
		params.put("usernum", loginUser(session).getUsernum());
		int totalCount = approvalService.countDrafts(params);
		int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));
		List<ApprovalItem> items = approvalService.getDrafts(params).stream()
				.map(ApprovalItem::from)
				.toList();
		return new ApprovalListResponse(items, page, totalPages, totalCount,
				mode, kind, status, keyword);
	}

	@GetMapping("/approvals/received")
	public ApprovalListResponse receivedApprovals(
			@RequestParam(defaultValue = "") String kind,
			@RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "1") int page,
			HttpSession session) {
		page = Math.max(page, 1);
		Map<String, Object> params = searchParams(kind, status, keyword, page);
		params.put("usernum", loginUser(session).getUsernum());
		int totalCount = approvalService.countReceived(params);
		return approvalList(approvalService.getReceived(params), page, totalCount,
				"received", kind, status, keyword);
	}

	@GetMapping("/approvals/completed")
	public ApprovalListResponse completedApprovals(
			@RequestParam(defaultValue = "") String kind,
			@RequestParam(defaultValue = "") String team,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "1") int page) {
		page = Math.max(page, 1);
		Map<String, Object> params = searchParams(kind, "", keyword, page);
		if (!team.isBlank() && !"부서".equals(team)) params.put("team", team);
		int totalCount = approvalService.countCompleted(params);
		return approvalList(approvalService.getCompleted(params), page, totalCount,
				"completed", kind, team, keyword);
	}

	@GetMapping("/approvals/write-options")
	public ApprovalWriteOptions approvalWriteOptions(HttpSession session) {
		userVO user = loginUser(session);
		return new ApprovalWriteOptions(UserSummary.from(user), LocalDate.now().toString(),
				approvalService.getAvailableApprovers(user).stream().map(UserSummary::from).toList());
	}

	@GetMapping("/approvals/{approvalNo}")
	public ApprovalDetailResponse approval(@PathVariable int approvalNo, HttpSession session) {
		userVO user = loginUser(session);
		approvalVO approval = approvalService.getAccessibleApproval(approvalNo, user.getUsernum());
		List<approval_line_VO> lines = approvalService.getLines(approvalNo);
		boolean owner = user.getUsernum().equals(approval.getUsernum());
		boolean editable = owner && List.of("대기중", "반려").contains(approval.getDocument_status());
		boolean approver = lines.stream().anyMatch(line -> user.getUsernum().equals(line.getApproval_target())
				&& "대기".equals(line.getApproval_status()));

		return new ApprovalDetailResponse(
				ApprovalDetail.from(approval),
				lines.stream().map(ApprovalLine::from).toList(),
				approvalService.getFiles(approvalNo).stream().map(ApprovalFile::from).toList(),
				editable, owner && "대기중".equals(approval.getDocument_status()), approver);
	}

	@GetMapping("/notices")
	public NoticeListResponse notices(
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "") String notice_team,
			@RequestParam(defaultValue = "") String searchWord) {
		page = Math.max(page, 1);
		searchVO search = new searchVO();
		search.setPageno(page);
		search.setNotice_team(notice_team);
		search.setSearchWord(searchWord);
		int totalCount = noticeService.countNotices(search);
		return new NoticeListResponse(
				noticeService.getNotices(search).stream().map(NoticeListItem::from).toList(),
				page, Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE)), totalCount,
				notice_team, searchWord);
	}

	@GetMapping("/notices/{noticeNo}")
	public NoticeDetail notice(@PathVariable int noticeNo, HttpSession session) {
		noticeVO notice = noticeService.getNotice(noticeNo);
		List<String> teams = noticeService.getNoticeTeams(noticeNo).stream()
				.map(notice_teamVO::getTeam_name).toList();
		String downloadUrl = notice.getFname() == null || notice.getFname().isBlank()
				? null : "/ERP/notice/down.do?notice_no=" + noticeNo;
		return NoticeDetail.from(notice, teams, downloadUrl, loginUser(session).isAuthority());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("message", exception.getMessage()));
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}

	private static Map<String, Object> searchParams(
			String kind, String status, String keyword, int page) {
		Map<String, Object> params = new HashMap<>();
		params.put("kind", "문서구분".equals(kind) ? "" : kind);
		params.put("status", status);
		params.put("keyword", keyword);
		params.put("offset", (page - 1) * PAGE_SIZE);
		params.put("limit", PAGE_SIZE);
		return params;
	}

	private static String statusForMode(String mode, String fallback) {
		return switch (mode) {
			case "1" -> "대기중";
			case "2" -> "반려";
			case "3" -> "진행중";
			case "4" -> "승인";
			default -> fallback;
		};
	}

	private static ApprovalListResponse approvalList(List<approvalVO> approvals, int page,
			int totalCount, String mode, String kind, String status, String keyword) {
		return new ApprovalListResponse(approvals.stream().map(ApprovalItem::from).toList(), page,
				Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE)), totalCount,
				mode, kind, status, keyword);
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

	public record NoticeListResponse(List<NoticeListItem> items, int page, int totalPages,
			int totalCount, String team, String keyword) {}

	public record NoticeListItem(int noticeNo, String title, boolean important, String date,
			String writer) {
		static NoticeListItem from(noticeVO notice) {
			return new NoticeListItem(notice.getNotice_no(), notice.getNotice_title(),
					notice.isIs_important(), notice.getnoticedate(), notice.getUsername());
		}
	}

	public record NoticeDetail(int noticeNo, String title, String content, boolean important,
			boolean main, String date, String writer, List<String> teams, String fileName,
			String downloadUrl, boolean canDelete) {
		static NoticeDetail from(noticeVO notice, List<String> teams, String downloadUrl,
				boolean canDelete) {
			return new NoticeDetail(notice.getNotice_no(), notice.getNotice_title(),
					notice.getNotice_content(), notice.isIs_important(), notice.isIs_main(),
					notice.getnoticedate(), notice.getUsername(), teams, notice.getFname(),
					downloadUrl, canDelete);
		}
	}

	public record ApprovalListResponse(List<ApprovalItem> items, int page, int totalPages,
			int totalCount, String mode, String kind, String status, String keyword) {}

	public record ApprovalItem(int approvalNo, String kind, String title, String writeDate,
			String status, UserSummary drafter) {
		static ApprovalItem from(approvalVO approval) {
			return new ApprovalItem(approval.getApproval_no(), approval.getKind(),
					approval.getApproval_title(), approval.getWritedate(),
					approval.getDocument_status(), UserSummary.from(approval.getDrafter()));
		}
	}

	public record ApprovalDetail(int approvalNo, String kind, String code, String title,
			String content, String writeDate, String status, String usernum, UserSummary drafter) {
		static ApprovalDetail from(approvalVO approval) {
			return new ApprovalDetail(approval.getApproval_no(), approval.getKind(),
					approval.getApproval_code(), approval.getApproval_title(),
					approval.getApproval_content(), approval.getWritedate(),
					approval.getDocument_status(), approval.getUsernum(),
					UserSummary.from(approval.getDrafter()));
		}
	}

	public record ApprovalLine(String target, String status, String order, String date,
			String comment, UserSummary approver) {
		static ApprovalLine from(approval_line_VO line) {
			return new ApprovalLine(line.getApproval_target(), line.getApproval_status(),
					line.getApproval_sort(), line.getApproval_date(), line.getComment(),
					UserSummary.from(line.getApprover()));
		}
	}

	public record ApprovalFile(int approvalNo, String name, String downloadUrl) {
		static ApprovalFile from(approval_file_VO file) {
			return new ApprovalFile(file.getApproval_no(), file.getAfname(),
					"/ERP/approval/down.do?no=" + file.getApproval_no());
		}
	}

	public record ApprovalDetailResponse(ApprovalDetail item, List<ApprovalLine> lines,
			List<ApprovalFile> files, boolean canEdit, boolean canDelete, boolean canApprove) {}

	public record ApprovalWriteOptions(UserSummary drafter, String date,
			List<UserSummary> approvers) {}
}
