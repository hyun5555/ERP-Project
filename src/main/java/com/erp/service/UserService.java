package com.erp.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.erp.mapper.UserMapper;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Service
public class UserService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void createUser(userVO user, userVO actor) {
		requireAdmin(actor);
		if (user.getUserpw() == null || user.getUserpw().isBlank()) {
			throw new IllegalArgumentException("초기 비밀번호는 필수입니다.");
		}
		user.setUserpw(passwordEncoder.encode(user.getUserpw()));
		user.setFirstlogin(true);
		user.setLevel_num(levelNumber(user.getLevel()));
		userMapper.insertUser(user);
	}

	@Transactional
	public void updateUser(userVO user, userVO actor) {
		requireAdmin(actor);
		if (user.getUserpw() != null && !user.getUserpw().isBlank()) {
			user.setUserpw(passwordEncoder.encode(user.getUserpw()));
		}
		user.setLevel_num(levelNumber(user.getLevel()));
		if (userMapper.updateUser(user) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사원 정보가 없습니다.");
		}
	}

	@Transactional
	public void deleteUser(String usernum, userVO actor) {
		requireAdmin(actor);
		if (userMapper.deleteUser(usernum) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사원 정보가 없습니다.");
		}
	}

	@Transactional
	public void deleteUsers(List<String> usernums, userVO actor) {
		requireAdmin(actor);
		if (usernums == null || usernums.isEmpty()) {
			throw new IllegalArgumentException("삭제할 사원을 선택해야 합니다.");
		}
		userMapper.deleteUsers(usernums);
	}

	public List<userVO> getUsers(searchVO search) {
		return userMapper.selectUsers(search);
	}

	public int countUsers(searchVO search) {
		return userMapper.countUsers(search);
	}

	public userVO getUser(String usernum) {
		userVO user = userMapper.selectUser(usernum);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사원 정보가 없습니다.");
		}
		return user;
	}

	public String nextUsernum(String year) {
		// ponytail: DB PK가 동시 등록 충돌을 차단한다. 등록량이 커지면 별도 시퀀스로 교체한다.
		return userMapper.nextUsernum(year);
	}

	private static void requireAdmin(userVO actor) {
		if (actor == null || !actor.isAuthority()) {
			throw new AccessDeniedException("관리자만 사원 정보를 변경할 수 있습니다.");
		}
	}

	private static int levelNumber(String level) {
		return switch (level == null ? "" : level) {
			case "사장" -> 100;
			case "팀장" -> 200;
			case "대리" -> 300;
			case "사원" -> 400;
			default -> 999;
		};
	}
}
