package com.erp.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
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
	private final UserService userService;
	private final AttachmentStorage attachments;

	public NoticeService(NoticeMapper noticeMapper, UserService userService, AttachmentStorage attachments) {
		this.noticeMapper = noticeMapper;
		this.userService = userService;
		this.attachments = attachments;
	}

	@Transactional
	public int createNotice(noticeVO notice, String[] teams, userVO actor) {
		userVO current = userService.requireAdmin(actor);
		if (teams == null || teams.length == 0) {
			throw new IllegalArgumentException("공지 대상을 한 곳 이상 선택해야 합니다.");
		}

		notice.setUsernum(current.getUsernum());
		noticeMapper.insertNotice(notice);
		noticeMapper.insertNoticeTeams(notice.getNotice_no(), Arrays.asList(teams));
		return notice.getNotice_no();
	}

	@Transactional
	public noticeVO deleteNotice(int noticeNo, userVO actor) {
		userService.requireAdmin(actor);
		noticeVO notice = getNotice(noticeNo);
		if (noticeMapper.deleteNotice(noticeNo) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항이 없습니다.");
		}
		if (notice.getPname() != null) attachments.deleteAfterCommit(List.of(notice.getPname()));
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
		return findForAi(user, null, "");
	}

	public List<noticeVO> findForAi(userVO user, Integer noticeNo, String title) {
		return noticeMapper.selectRecentForUser(user.getUsernum(), noticeNo, title);
	}

}
