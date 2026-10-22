package com.erp.service;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Collections;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
	private final SessionRevocationService sessions;

	public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
			SessionRevocationService sessions) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
	}

	@Transactional
	public void createUser(userVO user, userVO actor) {
		requireAdmin(actor);
		validate(user);
		if (user.getUserpw() == null || user.getUserpw().isBlank()) {
			throw new IllegalArgumentException("초기 비밀번호는 필수입니다.");
		}
		user.setUserpw(passwordEncoder.encode(user.getUserpw()));
		user.setFirstlogin(true);
		user.setLevel_num(levelNumber(user.getLevel()));
		if (blank(user.getUsernum()) || user.getUsernum().matches("[0-9]{7}")) {
			String year = String.valueOf(Year.now().getValue());
			userMapper.ensureUserNumberSequence(year);
			int number = userMapper.selectUserNumberForUpdate(year) + 1;
			// The sequence row serializes new numbers; legacy rows only establish the initial lower bound.
			String lastUsernum = userMapper.selectLastUsernum(year);
			if (lastUsernum != null) {
				number = Math.max(number, Integer.parseInt(lastUsernum.substring(4)) + 1);
			}
			user.setUsernum(numberedUsernum(year, number));
			userMapper.updateUserNumberSequence(year, number);
		}
		try {
			userMapper.insertUser(user);
		} catch (DuplicateKeyException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 사원번호입니다.", exception);
		}
	}

	@Transactional
	public void updateUser(userVO user, userVO actor) {
		lockForChange(actor, Collections.singletonList(user.getUsernum()));
		userVO previous = lockedUser(user.getUsernum());
		user.setUsernum(previous.getUsernum());
		validate(user);
		if (user.getUserpw() != null && !user.getUserpw().isBlank()) {
			user.setUserpw(passwordEncoder.encode(user.getUserpw()));
		} else {
			user.setUserpw(null);
		}
		user.setLevel_num(levelNumber(user.getLevel()));
		if (userMapper.updateUser(user) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사원 정보가 없습니다.");
		}
		if (previous.isAuthority() != user.isAuthority()
				|| !previous.getUser_status().equals(user.getUser_status())) {
			sessions.revokeAfterCommit(previous.getUsernum());
		}
	}

	@Transactional
	public void deleteUser(String usernum, userVO actor) {
		deleteUsers(Collections.singletonList(usernum), actor);
	}

	@Transactional
	public void deleteUsers(List<String> usernums, userVO actor) {
		if (usernums == null || usernums.isEmpty()) {
			throw new IllegalArgumentException("삭제할 사원을 선택해야 합니다.");
		}
		lockForChange(actor, usernums);
		List<String> canonical = new ArrayList<>();
		for (String requested : usernums) {
			String usernum = lockedUser(requested).getUsernum();
			if (userMapper.hasBusinessReferences(usernum)) throw historyConflict(null);
			canonical.add(usernum);
		}
		canonical = new ArrayList<>(new LinkedHashSet<>(canonical));
		try {
			userMapper.deleteUsers(canonical);
		} catch (DataIntegrityViolationException exception) {
			// RESTRICT is the final guard if a new reference races with deletion.
			throw historyConflict(exception);
		}
		canonical.forEach(sessions::revokeAfterCommit);
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
		if (year == null || !year.matches("[0-9]{4}")) {
			throw new IllegalArgumentException("사원번호 발급 연도를 확인해 주세요.");
		}
		return numberedUsernum(year, userMapper.nextUserNumber(year));
	}

	private static String numberedUsernum(String year, int number) {
		if (number > 999) {
			throw new IllegalArgumentException(year + "년 자동 사원번호(001~999)를 모두 사용했습니다.");
		}
		return year + String.format(Locale.ROOT, "%03d", number);
	}

	public userVO requireAdmin(userVO actor) {
		lockForChange(actor, List.of());
		return userMapper.selectUserForUpdate(actor.getUsernum());
	}

	private void lockForChange(userVO actor, List<String> targets) {
		if (actor == null || blank(actor.getUsernum())) {
			throw new AccessDeniedException("관리자만 사원 정보를 변경할 수 있습니다.");
		}
		List<String> usernums = new ArrayList<>(targets);
		usernums.add(actor.getUsernum());
		if (usernums.stream().anyMatch(UserService::blank)) {
			throw new IllegalArgumentException("사원번호를 확인해 주세요.");
		}
		userMapper.selectUsersForUpdate(usernums);
		userVO current = userMapper.selectUserForUpdate(actor.getUsernum());
		if (current == null || !current.isAuthority() || !"재직".equals(current.getUser_status())) {
			throw new AccessDeniedException("관리자만 사원 정보를 변경할 수 있습니다.");
		}
	}

	private userVO lockedUser(String usernum) {
		userVO user = userMapper.selectUserForUpdate(usernum);
		if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사원 정보가 없습니다.");
		return user;
	}

	private static ResponseStatusException historyConflict(Throwable cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT,
				"업무 이력이 있는 사원은 삭제할 수 없습니다. 퇴직 상태로 변경해 주세요.", cause);
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

	private static void validate(userVO user) {
		if (user.getUsernum() != null && user.getUsernum().length() > 50) {
			throw new IllegalArgumentException("사원번호는 50자 이내여야 합니다.");
		}
		if (blank(user.getName()) || blank(user.getTeam()) || blank(user.getLevel())
				|| blank(user.getUser_status())) {
			throw new IllegalArgumentException("사원명, 부서, 직급, 근무상태는 필수입니다.");
		}
		if (blank(user.getIdnum1()) || !user.getIdnum1().matches("\\d{6}")
				|| blank(user.getIdnum2()) || !user.getIdnum2().matches("\\d")) {
			throw new IllegalArgumentException("주민번호 형식을 확인해 주세요.");
		}
		if (blank(user.getPhonenum()) || blank(user.getOfficenum())
				|| blank(user.getEmail()) || !user.getEmail().contains("@")) {
			throw new IllegalArgumentException("연락처와 이메일을 확인해 주세요.");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
