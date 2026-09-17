package com.erp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.vo.noticeVO;
import com.erp.vo.notice_teamVO;
import com.erp.vo.searchVO;

@Mapper
public interface NoticeMapper {

	int insertNotice(noticeVO notice);

	int insertNoticeTeams(@Param("noticeNo") int noticeNo, @Param("teams") List<String> teams);

	int countNotices(searchVO search);

	List<noticeVO> selectNotices(searchVO search);

	noticeVO selectNotice(int noticeNo);

	List<notice_teamVO> selectNoticeTeams(int noticeNo);

	List<noticeVO> selectMainNotices();

	int deleteNotice(int noticeNo);
}
