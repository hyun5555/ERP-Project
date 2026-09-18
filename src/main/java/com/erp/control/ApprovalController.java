package com.erp.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
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
	public String approvalRecv() {
		return "app";
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
	public String approvalWrite() {
		return "app";
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
			HttpSession session) {
		approvalService.getEditableApproval(approval_no, loginUser(session).getUsernum());
		return "app";
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
	public String approvalAllok() {
		return "app";
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

}
