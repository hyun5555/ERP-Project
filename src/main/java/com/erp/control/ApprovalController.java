package com.erp.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import com.erp.service.ApprovalService;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.userVO;

@Controller
public class ApprovalController {

	private static final int PAGE_SIZE = 10;

	private final ApprovalService approvalService;
	private final Path uploadDirectory;

	public ApprovalController(ApprovalService approvalService,
			@Value("${erp.upload-dir}") String uploadDirectory) {
		this.approvalService = approvalService;
		this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
	}

	@GetMapping("/approval/list.do")
	public String approvalList() {
		return "app";
	}

	@GetMapping("/approval/recv.do")
	public String approvalRecv(
			@RequestParam(defaultValue = "") String kind,
			@RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "1") int page,
			HttpSession session, Model model) {
		Map<String, Object> params = searchParams(kind, status, keyword, page);
		params.put("usernum", loginUser(session).getUsernum());
		int totalCount = approvalService.countReceived(params);

		addPagination(model, page, totalCount);
		model.addAttribute("approvalList", approvalService.getReceived(params));
		model.addAttribute("kind", kind);
		model.addAttribute("status", status);
		model.addAttribute("keyword", keyword);
		model.addAttribute("app", new approvalVO());
		return "approval/recv";
	}

	@GetMapping("/approval/view.do")
	public String approvalView(@RequestParam int approval_no, HttpSession session) {
		approvalService.getAccessibleApproval(approval_no, loginUser(session).getUsernum());
		return "app";
	}

	@PostMapping("/approval/delete.do")
	public String deleteApproval(@RequestParam int approval_no, HttpSession session) {
		approvalService.deleteApproval(approval_no, loginUser(session).getUsernum());
		return "redirect:/approval/list.do";
	}

	@PostMapping("/approval/updateComment.do")
	public String processApproval(@RequestParam int approval_no,
			@RequestParam String approval_status,
			@RequestParam(defaultValue = "") String comment,
			HttpSession session) {
		approvalService.processApproval(approval_no, loginUser(session).getUsernum(),
				approval_status, comment);
		return "redirect:/approval/view.do?approval_no=" + approval_no;
	}

	@GetMapping("/approval/down.do")
	public void download(@RequestParam int no, HttpSession session,
			HttpServletResponse response) throws IOException {
		approvalService.getAccessibleApproval(no, loginUser(session).getUsernum());
		List<approval_file_VO> files = approvalService.getFiles(no);
		if (files.isEmpty()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		approval_file_VO file = files.get(0);
		Path path = uploadDirectory.resolve(file.getApname()).normalize();
		if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		String encodedName = new String(file.getAfname().getBytes("UTF-8"), "ISO-8859-1");
		response.setContentType("application/download");
		response.setContentLengthLong(Files.size(path));
		response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
		Files.copy(path, response.getOutputStream());
	}

	@GetMapping("/approval/write.do")
	public String approvalWrite(HttpSession session, Model model) {
		userVO loginUser = loginUser(session);
		approvalVO approval = new approvalVO();
		approval.setWritedate(LocalDate.now().toString());
		approval.setUsernum(loginUser.getUsernum());

		model.addAttribute("appVO", approval);
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("modalList", approvalService.getAvailableApprovers(loginUser));
		return "approval/write";
	}

	@PostMapping("/approval/write.do")
	public String createApproval(approvalVO approval,
			@RequestParam(name = "attach", required = false) MultipartFile attachment,
			@RequestParam(name = "approval_target", required = false) List<String> approvers,
			HttpSession session) throws IOException {
		List<approval_file_VO> files = store(attachment);
		try {
			int approvalNo = approvalService.createApproval(approval,
					loginUser(session).getUsernum(), files, approvers);
			return "redirect:/approval/view.do?approval_no=" + approvalNo;
		} catch (RuntimeException exception) {
			deleteStored(files);
			throw exception;
		}
	}

	@GetMapping("/approval/modify.do")
	public String approvalModify(@RequestParam int approval_no,
			HttpSession session, Model model) {
		userVO loginUser = loginUser(session);
		approvalVO approval = approvalService.getEditableApproval(approval_no, loginUser.getUsernum());
		approval.setWritedate(LocalDate.now().toString());

		model.addAttribute("item", approval);
		model.addAttribute("appfileList", approvalService.getFiles(approval_no));
		model.addAttribute("addedLine", approvalService.getLines(approval_no));
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("modalList", approvalService.getAvailableApprovers(loginUser));
		return "approval/modify";
	}

	@PostMapping("/approval/modify.do")
	public String modifyApproval(approvalVO approval,
			@RequestParam int approval_no,
			@RequestParam(name = "attach", required = false) MultipartFile attachment,
			@RequestParam(name = "approval_target", required = false) List<String> approvers,
			HttpSession session) throws IOException {
		approval.setApproval_no(approval_no);
		List<approval_file_VO> files = store(attachment);
		try {
			approvalService.modifyApproval(approval, loginUser(session).getUsernum(), files, approvers);
			return "redirect:/approval/view.do?approval_no=" + approval_no;
		} catch (RuntimeException exception) {
			deleteStored(files);
			throw exception;
		}
	}

	@GetMapping("/approval/allok.do")
	public String approvalAllok(
			@RequestParam(defaultValue = "") String kind,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "") String team,
			@RequestParam(defaultValue = "1") int page,
			Model model) {
		Map<String, Object> params = searchParams(kind, "", keyword, page);
		if (!"부서".equals(team)) {
			params.put("team", team);
		}
		int totalCount = approvalService.countCompleted(params);
		addPagination(model, page, totalCount);
		model.addAttribute("ListAll", approvalService.getCompleted(params));
		model.addAttribute("kind", kind);
		model.addAttribute("keyword", keyword);
		model.addAttribute("team", team);
		return "approval/allok";
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public String badRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}

	private List<approval_file_VO> store(MultipartFile attachment) throws IOException {
		if (attachment == null || attachment.isEmpty()) {
			return List.of();
		}
		Files.createDirectories(uploadDirectory);
		String savedName = UUID.randomUUID().toString();
		attachment.transferTo(uploadDirectory.resolve(savedName));

		approval_file_VO file = new approval_file_VO();
		file.setAfname(attachment.getOriginalFilename());
		file.setApname(savedName);
		return List.of(file);
	}

	private void deleteStored(List<approval_file_VO> files) {
		for (approval_file_VO file : files) {
			try {
				Files.deleteIfExists(uploadDirectory.resolve(file.getApname()));
			} catch (IOException ignored) {
				// DB rollback is complete; an orphaned upload can be removed by maintenance.
			}
		}
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}

	private static Map<String, Object> searchParams(
			String kind, String status, String keyword, int page) {
		Map<String, Object> params = new HashMap<>();
		if (!"문서구분".equals(kind)) {
			params.put("kind", kind);
		}
		params.put("status", status);
		params.put("keyword", keyword);
		params.put("offset", Math.max(0, page - 1) * PAGE_SIZE);
		params.put("limit", PAGE_SIZE);
		return params;
	}

	private static void addPagination(Model model, int page, int totalCount) {
		int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
		int startBlock = (page - 1) - ((page - 1) % 10) + 1;
		model.addAttribute("page", page);
		model.addAttribute("startbk", startBlock);
		model.addAttribute("endbk", Math.min(startBlock + 9, totalPages));
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("totalpage", totalPages);
	}

}
