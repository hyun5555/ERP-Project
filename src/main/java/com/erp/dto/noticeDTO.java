package com.erp.dto;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.erp.vo.*;

@Repository
public class noticeDTO 
{
	@Autowired
	private SqlSession session;
	
	private final static String namespace = "com.erp.notice";
	
	//C:공지사항을 등록한다.
	//매개변수 : vo - 등록할 공지사항 정보
	//리턴값 : true - 등록 성공, false - 등록 실패	
	public boolean noticeInsert(noticeVO vo,String[] notice_team)
	{
		session.insert(namespace + ".notice_insert",vo);
	
		for(String team : notice_team)
		{
			notice_teamVO teamVO = new notice_teamVO();
			teamVO.setNotice_no(vo.getNotice_no());
			teamVO.setNotice_team(team);
			//System.out.println("teamVO.getNotice_no():" + teamVO.getNotice_no());
			//System.out.println("teamVO.getNotice_team():" + teamVO.getNotice_team());
			session.insert(namespace + ".insert_team",teamVO);
		}
		
		return true;
	}
	
	//R:공지사항 전체 갯수를 얻는다.
	//매개변수 : searchVO -> notice_team -> 목록 정리 
	//리턴값 : 전체 공지사항 갯수
	public int GetTotal(searchVO vo)
	{
		int total = session.selectOne(namespace + ".total",vo);
		
		return total;
	}
	
	//R:공지사항 목록을 조회한다.
	//매개변수 : searchVO
	//리턴값 : 공지사항 목록 List
	public List<noticeVO> noticeList(searchVO vo)
	{
		List<noticeVO> list = session.selectList(namespace + ".notice_list",vo);
		return list;
	}
	
	// 부서 리스트 (탭 메뉴용)
	public List<notice_teamVO> noticeTeamList() {
	    return session.selectList(namespace + ".notice_team_list");
	}
	
	//R:공지사항 정보를 조회한다.
	//매개변수 : notice_no - 조회할 공지사항 번호 
	//리턴값 : noticeVO - 공지사항 정보
	public noticeVO getNoticeInfo(int notice_no)
	{
		noticeVO vo = session.selectOne(namespace + ".notice_view",notice_no);
		
		return vo;
	}
	
	//R:공지사항의 팀정보를 정보를 조회한다.
	//매개변수 : notice_no - 조회할 공지사항 번호 
	//리턴값 : notice_teamVO - 공지사항 부서 목록 정보
	public List<notice_teamVO> getNoticeTeam(int notice_no)
	{
		 List<notice_teamVO> list = session.selectList(namespace + ".notice_team",notice_no);
		
		return list;
	}	
	
	//R:메인화면에 공지사항 목록을 조회한다.
	//매개변수 : 
	//리턴값 : 공지사항 목록 List
	public List<noticeVO> mainList()
	{
		List<noticeVO> list = session.selectList(namespace + ".main_List");
		return list;
	}
	
	//D:공지사항 정보를 삭제한다.
	//매개변수 : notice_no - 삭제 할 공지사항 번호
	//리턴값 : true - 삭제 성공, false - 삭제 실패	
	public boolean noticeDelete(int notice_no) 
	{
		noticeVO vo = new noticeVO();
		vo.setNotice_no(notice_no);;
		
		session.delete(namespace + ".notice_delete",vo);
		return true;
	}
	
	
}
