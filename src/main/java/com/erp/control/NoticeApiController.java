package com.erp.control;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.erp.service.NoticeService;
import com.erp.vo.noticeVO;
import com.erp.vo.notice_teamVO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api/notices")
public class NoticeApiController {

	private static final int PAGE_SIZE = 10;

	private final NoticeService noticeService;

	public NoticeApiController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@GetMapping
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

	@GetMapping("/{noticeNo}")
	public NoticeDetail notice(@PathVariable int noticeNo, HttpSession session) {
		noticeVO notice = noticeService.getNotice(noticeNo);
		List<String> teams = noticeService.getNoticeTeams(noticeNo).stream()
				.map(notice_teamVO::getTeam_name).toList();
		String downloadUrl = notice.getFname() == null || notice.getFname().isBlank()
				? null : "/ERP/notice/down.do?notice_no=" + noticeNo;
		userVO user = (userVO) session.getAttribute("loginUser");
		return NoticeDetail.from(notice, teams, downloadUrl, user.isAuthority());
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
}
