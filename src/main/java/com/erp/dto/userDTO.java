package com.erp.dto;

import org.apache.ibatis.session.SqlSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.erp.vo.*;

@Repository
public class userDTO {
	
	private final SqlSession session;
	private final PasswordEncoder passwordEncoder;

	public userDTO(SqlSession session, PasswordEncoder passwordEncoder) {
		this.session = session;
		this.passwordEncoder = passwordEncoder;
	}
	
	private final static String namespace = "com.erp.user";
	
	//C:사원을 등록한다.
	//매개변수 : loginUser - 로그인한 사원정보,  vo - 등록할 사원 정보,
	//리턴값 : true - 등록 성공, false - 등록 실패	
	public boolean insertUser(userVO loginUser, userVO vo) {
	    // 관리자 권한이 없으면 false 리턴
	    if (!loginUser.isAuthority()) {
	        return false;
	    }

	    vo.setUserpw(passwordEncoder.encode(vo.getUserpw()));
	    session.insert(namespace + ".insert", vo);
	    return true;
	}
	
	//기능 : 사원의 전체 리스트 반환
	//매개변수 : vo - 전체 사원 목록
	//리턴값 : 사원목록 userList
	public List<userVO> getAllUserList(searchVO vo){
		List<userVO> userList = session.selectList(namespace + ".user_list",vo);
		return userList;
	}
	
	//기능 : 전체 사원 수 반환
	//매개변수 : vo - 전체 사원 수 반환
	//리턴값 : 사원 총 수
	public int getTotalCount(searchVO vo) {
		return session.selectOne(namespace + ".user_total_count", vo);
	}

	
	//기능 : 모달창 사원 정보 리스트
	//매개변수 : vo - 전체 사원 목록
	//리턴값 : 사원리스트
	public List<userVO>getModalList(userVO vo){
		List<userVO> userList = session.selectList(namespace + ".user_modal_list",vo);
		return userList;
	}
	
	//기능 : 단독 사원 정보 조회
	//매개변수 : 사원번호
	//리턴값 : 사원정보
	public userVO getUserInfo(String usernum) {
		userVO vo = session.selectOne(namespace + ".user_info", usernum);
		return vo;
	}
	 
	
	//기능 : 사원관리에서 usernum이 중복된 사원번호인지 검사
	//매개변수 : usernum - 사원번호
	//리턴값 : true - 아이디 중복됨, false - 아이디 중복 안됨
	public boolean isDupCheckId(String usernum) {
		int total = session.selectOne(namespace + ".checkid", usernum);
		if(total > 0) {
			return true;
		}
		return false;
	}
	
	//U: 사원 정보를 변경한다.
	//매개변수 : vo - 변경 할 사원 정보
	//리턴값 : true - 변경 성공, false - 변경 실패	
	public boolean updateUserInfo(userVO vo) {
		if (vo.getUserpw() != null && !vo.getUserpw().isBlank()) {
			vo.setUserpw(passwordEncoder.encode(vo.getUserpw()));
		}
		session.update(namespace + ".update", vo); 
		return true;
	}
	
	//D: 사원를 삭제한다.
	//매개변수 : usernum - 변경 할 사원번호
	//리턴값 : true - 변경 성공, false - 변경 실패	
	//public userVO 
	public boolean userDelete(String usernum) {
		userVO vo = new userVO();
		vo.setUsernum(usernum);

		int result = session.delete(namespace + ".delete", vo);
		return result > 0;  // 삭제된 행 수가 1 이상이면 true 반환
	}

	
	//사원번호가 있는지 검사한다.
	public int FindUsernum(String usernum)
	{
		int count = session.selectOne(namespace + ".findnum", usernum);
		System.out.println("count:" + count);
		return count;
	}

}
