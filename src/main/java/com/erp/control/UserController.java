package com.erp.control;

import java.time.Year;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.erp.service.UserService;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Controller
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/user/list.do")
	public String userList(@ModelAttribute("searchVO") searchVO search, Model model) {
		if (search.getPageno() < 1) {
			search.setPageno(1);
		}
		int total = userService.countUsers(search);
		model.addAttribute("userList", userService.getUsers(search));
		model.addAttribute("pageno", search.getPageno());
		model.addAttribute("totalPages", (int) Math.ceil((double) total / 10));
		return "user/list";
	}

	@GetMapping("/user/view.do")
	public String userView(@RequestParam String usernum, Model model) {
		model.addAttribute("user", userService.getUser(usernum));
		return "user/view";
	}

	@GetMapping("/user/modify.do")
	public String userModify(@RequestParam String usernum, Model model) {
		userVO user = userService.getUser(usernum);
		model.addAttribute("user", user);
		addParts(model, "phonenum", user.getPhonenum(), "-");
		addParts(model, "officenum", user.getOfficenum(), "-");
		addParts(model, "email", user.getEmail(), "@");
		return "user/modify";
	}

	@PostMapping("/user/modify.do")
	public String updateUser(userVO user, HttpServletRequest request, HttpSession session) {
		user.setPhonenum(join(request, "phonenum", "-", 3));
		user.setOfficenum(join(request, "officenum", "-", 3));
		user.setEmail(join(request, "email", "@", 2));
		userService.updateUser(user, loginUser(session));
		return "redirect:/user/view.do?usernum=" + user.getUsernum();
	}

	@GetMapping("/user/write.do")
	public String userWrite(Model model) {
		model.addAttribute("usernum", userService.nextUsernum(String.valueOf(Year.now().getValue())));
		return "user/write";
	}

	@PostMapping("/user/write.do")
	public String createUser(userVO user, HttpSession session) {
		userService.createUser(user, loginUser(session));
		return "redirect:/user/list.do";
	}

	@PostMapping("/user/delete.do")
	public String deleteUser(@RequestParam String usernum, HttpSession session) {
		userService.deleteUser(usernum, loginUser(session));
		return "redirect:/user/list.do";
	}

	@GetMapping("/user/myinfo.do")
	public String myInfo(HttpSession session, Model model) {
		model.addAttribute("user", userService.getUser(loginUser(session).getUsernum()));
		return "user/myinfo";
	}

	@PostMapping("/user/deleteSelected.do")
	public String deleteSelected(@RequestParam("usernums") List<String> usernums,
			HttpSession session) {
		userService.deleteUsers(usernums, loginUser(session));
		return "redirect:/user/list.do";
	}

	@ResponseBody
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(IllegalArgumentException.class)
	public String badRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}

	private static void addParts(Model model, String name, String value, String separator) {
		String[] parts = value == null ? new String[0] : value.split(separator, -1);
		for (int index = 0; index < parts.length; index++) {
			model.addAttribute(name + (index + 1), parts[index]);
		}
	}

	private static String join(HttpServletRequest request, String name, String separator, int count) {
		String[] parts = new String[count];
		for (int index = 0; index < count; index++) {
			parts[index] = request.getParameter(name + (index + 1));
			if (parts[index] == null) {
				throw new IllegalArgumentException("필수 연락처 항목이 누락되었습니다.");
			}
		}
		return String.join(separator, parts);
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}
}
