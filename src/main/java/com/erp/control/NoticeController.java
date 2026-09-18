package com.erp.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import com.erp.service.NoticeService;
import com.erp.vo.noticeVO;
import com.erp.vo.userVO;

@Controller
public class NoticeController {

	private final NoticeService noticeService;
	private final Path uploadDirectory;

	public NoticeController(NoticeService noticeService,
			@Value("${erp.upload-dir}") String uploadDirectory) {
		this.noticeService = noticeService;
		this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
	}

	@GetMapping("/notice/list.do")
	public String noticeList() {
		return "app";
	}

	@GetMapping("/notice/view.do")
	public String noticeView(@RequestParam("notice_no") int noticeNo) {
		noticeService.getNotice(noticeNo);
		return "app";
	}

	@GetMapping("/notice/down.do")
	public void download(@RequestParam("notice_no") int noticeNo,
			HttpServletResponse response) throws IOException {
		noticeVO notice = noticeService.getNotice(noticeNo);
		if (notice.getPname() == null || notice.getPname().isBlank()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		Path path = uploadDirectory.resolve(notice.getPname()).normalize();
		if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path)) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		response.setContentType("application/octet-stream");
		response.setContentLengthLong(Files.size(path));
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment()
						.filename(notice.getFname() == null ? "download" : notice.getFname(),
								StandardCharsets.UTF_8)
						.build().toString());
		Files.copy(path, response.getOutputStream());
	}

	@GetMapping("/notice/write.do")
	public String noticeWrite() {
		return "app";
	}

	@PostMapping("/notice/writeOK.do")
	public String createNotice(noticeVO notice,
			@RequestParam(name = "notice_team", required = false) String[] teams,
			@RequestParam(name = "fileInput", required = false) MultipartFile attachment,
			HttpSession session) throws IOException {
		store(attachment, notice);
		try {
			int noticeNo = noticeService.createNotice(notice, teams, loginUser(session));
			return "redirect:/notice/view.do?notice_no=" + noticeNo;
		} catch (RuntimeException exception) {
			deleteStored(notice);
			throw exception;
		}
	}

	@PostMapping("/notice/delete.do")
	public String deleteNotice(@RequestParam("notice_no") int noticeNo, HttpSession session) {
		noticeVO notice = noticeService.deleteNotice(noticeNo, loginUser(session));
		deleteStored(notice);
		return "redirect:/notice/list.do";
	}

	@ResponseBody
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(IllegalArgumentException.class)
	public String badRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}

	private void store(MultipartFile attachment, noticeVO notice) throws IOException {
		if (attachment == null || attachment.isEmpty()) {
			return;
		}
		Files.createDirectories(uploadDirectory);
		String savedName = UUID.randomUUID().toString();
		attachment.transferTo(uploadDirectory.resolve(savedName));
		notice.setFname(attachment.getOriginalFilename());
		notice.setPname(savedName);
	}

	private void deleteStored(noticeVO notice) {
		if (notice.getPname() == null || notice.getPname().isBlank()) {
			return;
		}
		try {
			Path path = uploadDirectory.resolve(notice.getPname()).normalize();
			if (path.startsWith(uploadDirectory)) {
				Files.deleteIfExists(path);
			}
		} catch (IOException ignored) {
			// DB 처리는 끝났으며, 남은 고아 파일은 유지보수 작업에서 제거한다.
		}
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}
}
