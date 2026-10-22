package com.erp.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.WebDataBinder;

import com.erp.service.AttachmentStorage;
import com.erp.service.NoticeService;
import com.erp.vo.noticeVO;
import com.erp.vo.userVO;

@Controller
public class NoticeController {

	private final NoticeService noticeService;
	private final Path uploadDirectory;
	private final AttachmentStorage attachments;

	public NoticeController(NoticeService noticeService, AttachmentStorage attachments,
			@Value("${erp.upload-dir}") String uploadDirectory) {
		this.noticeService = noticeService;
		this.attachments = attachments;
		this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
	}

	@InitBinder
	public void noticeFields(WebDataBinder binder) {
		binder.setAllowedFields("notice_title", "notice_content", "is_important", "is_main");
	}

	@GetMapping("/notice/list.do")
	public String noticeList() {
		return "forward:/resources/app/index.html";
	}

	@GetMapping("/notice/view.do")
	public String noticeView(@RequestParam("notice_no") int noticeNo) {
		noticeService.getNotice(noticeNo);
		return "forward:/resources/app/index.html";
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
		return "forward:/resources/app/index.html";
	}

	@PostMapping("/notice/writeOK.do")
	public String createNotice(noticeVO notice,
			@RequestParam(name = "notice_team", required = false) String[] teams,
			@RequestParam(name = "fileInput", required = false) MultipartFile attachment,
			HttpSession session) throws IOException {
		notice.setFname(null);
		notice.setPname(null);
		AttachmentStorage.StoredFile stored = attachments.store(attachment);
		if (stored != null) {
			notice.setFname(stored.originalName());
			notice.setPname(stored.storedName());
		}
		try {
			int noticeNo = noticeService.createNotice(notice, teams, loginUser(session));
			return "redirect:/notice/view.do?notice_no=" + noticeNo;
		} catch (RuntimeException exception) {
			if (stored != null) attachments.deleteIfUnreferenced(stored.storedName());
			throw exception;
		}
	}

	@PostMapping("/notice/delete.do")
	public String deleteNotice(@RequestParam("notice_no") int noticeNo, HttpSession session) {
		noticeService.deleteNotice(noticeNo, loginUser(session));
		return "redirect:/notice/list.do";
	}

	@ResponseBody
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(IllegalArgumentException.class)
	public String badRequest(IllegalArgumentException exception) {
		return exception.getMessage();
	}

	private static userVO loginUser(HttpSession session) {
		return (userVO) session.getAttribute("loginUser");
	}
}
