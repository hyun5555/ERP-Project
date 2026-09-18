package com.erp.control;

import java.time.Year;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.erp.service.LoginService;
import com.erp.service.UserService;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api/users")
public class UserApiController {

	private static final int PAGE_SIZE = 10;

	private final UserService userService;
	private final LoginService loginService;

	public UserApiController(UserService userService, LoginService loginService) {
		this.userService = userService;
		this.loginService = loginService;
	}

	@GetMapping
	public UserListResponse users(
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "") String team,
			@RequestParam(defaultValue = "") String level,
			@RequestParam(defaultValue = "") String user_status,
			@RequestParam(defaultValue = "usernum") String searchKey,
			@RequestParam(defaultValue = "") String searchWord) {
		page = Math.max(page, 1);
		searchVO search = new searchVO();
		search.setPageno(page);
		search.setTeam(team);
		search.setLevel(level);
		search.setUser_status(user_status);
		search.setSearchKey(searchKey);
		search.setSearchWord(searchWord);
		int totalCount = userService.countUsers(search);
		return new UserListResponse(userService.getUsers(search).stream().map(UserDetail::from).toList(),
				page, Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE)), totalCount);
	}

	@GetMapping("/next-number")
	public Map<String, String> nextNumber() {
		return Map.of("usernum", userService.nextUsernum(String.valueOf(Year.now().getValue())));
	}

	@GetMapping("/me")
	public UserDetail me(HttpSession session) {
		return UserDetail.from(userService.getUser(loginUser(session).getUsernum()));
	}

	@PostMapping("/me/password")
	public Map<String, String> changePassword(@RequestBody PasswordChangeRequest request,
			HttpSession session) {
		if (request.newPassword() == null || !request.newPassword().equals(request.confirmPassword())) {
			throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
		}
		userVO user = loginUser(session);
		if (!loginService.changePassword(user.getUsernum(), request.currentPassword(), request.newPassword())) {
			throw new IllegalArgumentException("현재 비밀번호를 확인해 주세요.");
		}
		session.setAttribute("loginUser", loginService.findByUsernum(user.getUsernum()));
		return Map.of("message", "비밀번호가 변경되었습니다.");
	}

	@GetMapping("/{usernum}")
	public UserDetail user(@PathVariable String usernum) {
		return UserDetail.from(userService.getUser(usernum));
	}

	@PostMapping
	public Map<String, String> create(@RequestBody UserRequest request, HttpSession session) {
		userVO user = request.toUser(null);
		userService.createUser(user, loginUser(session));
		return Map.of("usernum", user.getUsernum());
	}

	@PutMapping("/{usernum}")
	public Map<String, String> update(@PathVariable String usernum,
			@RequestBody UserRequest request, HttpSession session) {
		userService.updateUser(request.toUser(usernum), loginUser(session));
		return Map.of("usernum", usernum);
	}

	@DeleteMapping("/{usernum}")
	public Map<String, String> delete(@PathVariable String usernum, HttpSession session) {
		userService.deleteUser(usernum, loginUser(session));
		return Map.of("usernum", usernum);
	}

	@PostMapping("/bulk-delete")
	public Map<String, Integer> deleteMany(@RequestBody List<String> usernums, HttpSession session) {
		userService.deleteUsers(usernums, loginUser(session));
		return Map.of("deleted", usernums.size());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("message", exception.getMessage()));
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}

	public record UserListResponse(List<UserDetail> items, int page, int totalPages, int totalCount) {}

	public record UserDetail(String usernum, String name, String idnum1, String idnum2,
			String phonenum, String officenum, String email, String team, String level,
			String joindate, boolean authority, String userStatus) {
		static UserDetail from(userVO user) {
			return new UserDetail(user.getUsernum(), user.getName(), user.getIdnum1(), user.getIdnum2(),
					user.getPhonenum(), user.getOfficenum(), user.getEmail(), user.getTeam(),
					user.getLevel(), user.getJoindate(), user.isAuthority(), user.getUser_status());
		}
	}

	public record UserRequest(String usernum, String userpw, String name, String idnum1,
			String idnum2, String phonenum, String officenum, String email, String team,
			String level, String joindate, boolean authority, String userStatus) {
		userVO toUser(String pathUsernum) {
			userVO user = new userVO();
			user.setUsernum(pathUsernum == null ? usernum : pathUsernum);
			user.setUserpw(userpw);
			user.setName(name);
			user.setIdnum1(idnum1);
			user.setIdnum2(idnum2);
			user.setPhonenum(phonenum);
			user.setOfficenum(officenum);
			user.setEmail(email);
			user.setTeam(team);
			user.setLevel(level);
			user.setJoindate(joindate);
			user.setAuthority(authority);
			user.setUser_status(userStatus);
			return user;
		}
	}

	public record PasswordChangeRequest(String currentPassword, String newPassword,
			String confirmPassword) {}
}
