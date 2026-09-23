package com.erp.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.erp.mapper.NoticeMapper;
import com.erp.vo.noticeVO;
import com.erp.vo.notice_teamVO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Service
public class NoticeService {

	private final NoticeMapper noticeMapper;

	public NoticeService(NoticeMapper noticeMapper) {
		this.noticeMapper = noticeMapper;
	}

	@Transactional
	public int createNotice(noticeVO notice, String[] teams, userVO actor) {
		requireAdmin(actor);
		if (teams == null || teams.length == 0) {
			throw new IllegalArgumentException("공지 대상을 한 곳 이상 선택해야 합니다.");
		}

		notice.setUsernum(actor.getUsernum());
		noticeMapper.insertNotice(notice);
		noticeMapper.insertNoticeTeams(notice.getNotice_no(), Arrays.asList(teams));
		return notice.getNotice_no();
	}

	@Transactional
	public noticeVO deleteNotice(int noticeNo, userVO actor) {
		requireAdmin(actor);
		noticeVO notice = getNotice(noticeNo);
		if (noticeMapper.deleteNotice(noticeNo) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항이 없습니다.");
		}
		return notice;
	}

	public noticeVO getNotice(int noticeNo) {
		noticeVO notice = noticeMapper.selectNotice(noticeNo);
		if (notice == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항이 없습니다.");
		}
		return notice;
	}

	public List<notice_teamVO> getNoticeTeams(int noticeNo) {
		return noticeMapper.selectNoticeTeams(noticeNo);
	}

	public List<noticeVO> getNotices(searchVO search) {
		return noticeMapper.selectNotices(search);
	}

	public int countNotices(searchVO search) {
		return noticeMapper.countNotices(search);
	}

	public List<noticeVO> getMainNotices() {
		return noticeMapper.selectMainNotices();
	}

	public List<noticeVO> getRecentForUser(userVO user) {
		String teamCode = switch (user.getTeam()) {
			case "개발" -> "100";
			case "디자인" -> "200";
			case "경영지원" -> "300";
			default -> null;
		};
		return noticeMapper.selectRecentForUser(teamCode, user.isAuthority());
	}

	private static void requireAdmin(userVO actor) {
		if (actor == null || !actor.isAuthority()) {
			throw new AccessDeniedException("관리자만 공지사항을 변경할 수 있습니다.");
		}
	}
}
