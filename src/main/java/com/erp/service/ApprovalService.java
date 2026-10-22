package com.erp.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erp.mapper.ApprovalMapper;
import com.erp.mapper.UserMapper;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.userVO;

@Service
public class ApprovalService {

	private static final Set<String> DECISIONS = Set.of("승인", "반려");

	private final ApprovalMapper approvalMapper;
	private final UserMapper userMapper;
	private final AttachmentStorage attachments;

	public ApprovalService(ApprovalMapper approvalMapper, UserMapper userMapper, AttachmentStorage attachments) {
		this.approvalMapper = approvalMapper;
		this.userMapper = userMapper;
		this.attachments = attachments;
	}

	@Transactional
	public int createApproval(approvalVO approval, String drafter,
			List<approval_file_VO> files, List<String> approvers) {
		List<String> canonical = validateApprovers(drafter, approvers);
		approval.setUsernum(userMapper.selectUserForUpdate(drafter).getUsernum());
		approvalMapper.insertApproval(approval);
		int approvalNo = approval.getApproval_no();

		if (files != null && !files.isEmpty()) {
			files.forEach(file -> file.setApproval_no(approvalNo));
			approvalMapper.insertApprovalFiles(files);
		}
		approvalMapper.insertApprovalLines(lines(approvalNo, canonical));
		return approvalNo;
	}

	@Transactional
	public void processApproval(int approvalNo, String approver, String decision, String comment) {
		if (!DECISIONS.contains(decision)) {
			throw new IllegalArgumentException("지원하지 않는 결재 상태입니다.");
		}
		if ("반려".equals(decision) && (comment == null || comment.isBlank())) {
			throw new IllegalArgumentException("반려 의견은 필수입니다.");
		}
		userMapper.selectUsersForUpdate(List.of(approver));
		userVO current = userMapper.selectUserForUpdate(approver);
		if (current == null || !"재직".equals(current.getUser_status()) || current.isAuthority()) {
			throw new AccessDeniedException("재직 중인 결재자만 결재할 수 있습니다.");
		}
		approver = current.getUsernum();
		approvalVO approval = approvalMapper.selectApprovalForUpdate(approvalNo);
		if (!canApprove(approval, approvalMapper.selectApprovalLinesForUpdate(approvalNo), approver)) {
			throw new AccessDeniedException("현재 결재 순서가 아니거나 이미 처리된 문서입니다.");
		}

		approval_line_VO line = new approval_line_VO();
		line.setApproval_no(approvalNo);
		line.setApproval_target(approver);
		line.setApproval_status(decision);
		line.setComment(comment);

		if (approvalMapper.updateApprovalLineStatus(line) != 1) {
			throw new AccessDeniedException("결재 권한이 없거나 이미 처리된 문서입니다.");
		}
		approvalMapper.updateDocumentStatusAuto(approvalNo);
	}

	@Transactional
	public void modifyApproval(approvalVO approval, String drafter,
			List<approval_file_VO> files, List<String> approvers) {
		List<String> canonical = validateApprovers(drafter, approvers);
		drafter = userMapper.selectUserForUpdate(drafter).getUsernum();
		approvalMapper.selectApprovalForUpdate(approval.getApproval_no());
		if (approvalMapper.updateApproval(approval, drafter) != 1) {
			throw new AccessDeniedException("작성자만 대기 또는 반려 문서를 수정할 수 있습니다.");
		}

		int approvalNo = approval.getApproval_no();
		approvalMapper.deleteApprovalLines(approvalNo);
		approvalMapper.insertApprovalLines(lines(approvalNo, canonical));
		if (files != null && !files.isEmpty()) {
			List<String> previousFiles = approvalMapper.selectApprovalFiles(approvalNo).stream()
					.map(approval_file_VO::getApname).toList();
			approvalMapper.deleteApprovalFiles(approvalNo);
			files.forEach(file -> file.setApproval_no(approvalNo));
			approvalMapper.insertApprovalFiles(files);
			attachments.deleteAfterCommit(previousFiles);
		}
	}

	@Transactional
	public void deleteApproval(int approvalNo, String drafter) {
		userMapper.selectUsersForUpdate(List.of(drafter));
		userVO current = userMapper.selectUserForUpdate(drafter);
		if (current == null || !"재직".equals(current.getUser_status())) {
			throw new AccessDeniedException("재직 중인 작성자만 문서를 삭제할 수 있습니다.");
		}
		drafter = current.getUsernum();
		approvalMapper.selectApprovalForUpdate(approvalNo);
		List<String> previousFiles = approvalMapper.selectApprovalFiles(approvalNo).stream()
				.map(approval_file_VO::getApname).toList();
		if (approvalMapper.deleteApproval(approvalNo, drafter) != 1) {
			throw new AccessDeniedException("작성자만 대기 문서를 삭제할 수 있습니다.");
		}
		attachments.deleteAfterCommit(previousFiles);
	}

	public approvalVO getAccessibleApproval(int approvalNo, String usernum) {
		approvalVO approval = approvalMapper.selectApprovalDetailForUser(approvalNo, usernum);
		if (approval == null) {
			throw new AccessDeniedException("문서 열람 권한이 없습니다.");
		}
		return approval;
	}

	public approvalVO getEditableApproval(int approvalNo, String usernum) {
		approvalVO approval = approvalMapper.selectApprovalDetail(approvalNo);
		if (approval == null || !usernum.equals(approval.getUsernum())
				|| !("대기중".equals(approval.getDocument_status())
						|| "반려".equals(approval.getDocument_status()))) {
			throw new AccessDeniedException("작성자만 대기 또는 반려 문서를 수정할 수 있습니다.");
		}
		return approval;
	}

	public approvalVO getApproval(int approvalNo) {
		return approvalMapper.selectApprovalDetail(approvalNo);
	}

	public List<approval_file_VO> getFiles(int approvalNo) {
		return approvalMapper.selectApprovalFiles(approvalNo);
	}

	public List<approval_line_VO> getLines(int approvalNo) {
		return approvalMapper.selectApprovalLines(approvalNo);
	}

	public boolean canApprove(approvalVO approval, List<approval_line_VO> lines, String usernum) {
		if (approval == null || !("대기중".equals(approval.getDocument_status())
				|| "진행중".equals(approval.getDocument_status()))) {
			return false;
		}
		userVO current = userMapper.selectUser(usernum);
		userVO drafter = userMapper.selectUser(approval.getUsernum());
		if (current == null || !"재직".equals(current.getUser_status()) || current.isAuthority()
				|| (drafter != null && current.getUsernum().equals(drafter.getUsernum()))) return false;
		// Both line queries return the registered numeric approval_sort order.
		for (approval_line_VO line : lines) {
			if (!"승인".equals(line.getApproval_status())) {
				userVO target = userMapper.selectUser(line.getApproval_target());
				return "대기".equals(line.getApproval_status()) && target != null
						&& current.getUsernum().equals(target.getUsernum());
			}
		}
		return false;
	}

	public List<userVO> getAvailableApprovers(userVO loginUser) {
		return approvalMapper.lineApproval(loginUser);
	}

	public List<approvalVO> getDrafts(Map<String, Object> params) {
		return approvalMapper.selectAppList(params);
	}

	public int countDrafts(Map<String, Object> params) {
		return approvalMapper.countAppList(params);
	}

	public List<approvalVO> getReceived(Map<String, Object> params) {
		return approvalMapper.selectRecvApprovalList(params);
	}

	public List<approvalVO> getPendingForApprover(String usernum, int limit) {
		return approvalMapper.selectPendingForApprover(usernum, Math.max(1, Math.min(limit, 20)), null, "");
	}

	public List<approvalVO> findPendingForAi(String usernum, Integer approvalNo, String title) {
		return approvalMapper.selectPendingForApprover(usernum, 5, approvalNo, title);
	}

	public int countReceived(Map<String, Object> params) {
		return approvalMapper.countRecvApprovalList(params);
	}

	public List<approvalVO> getCompleted(Map<String, Object> params) {
		return approvalMapper.selectListAll(params);
	}

	public int countCompleted(Map<String, Object> params) {
		return approvalMapper.selectListAllCount(params);
	}

	public Map<String, Integer> getCounts(String usernum) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("kind", "");
		params.put("usernum", usernum);
		params.put("keyword", "");
		params.put("offset", 0);
		params.put("limit", 1);

		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String status : List.of("대기중", "반려", "진행중", "승인")) {
			params.put("status", status);
			counts.put(status, countDrafts(params));
		}
		params.put("status", "");
		counts.put("수신", countReceived(params));
		return counts;
	}

	private List<String> validateApprovers(String drafter, List<String> approvers) {
		if (drafter == null || drafter.isBlank() || approvers == null || approvers.isEmpty()) {
			throw new IllegalArgumentException("작성자와 결재자를 지정해야 합니다.");
		}
		if (approvers.stream().anyMatch(value -> value == null || value.isBlank())) {
			throw new IllegalArgumentException("결재자를 확인해 주세요.");
		}
		List<String> participants = new ArrayList<>(approvers);
		participants.add(drafter);
		// The same ordered user locks serialize candidate validation with retirement and deletion.
		userMapper.selectUsersForUpdate(participants);
		userVO author = userMapper.selectUserForUpdate(drafter);
		if (author == null || !"재직".equals(author.getUser_status())) {
			throw new AccessDeniedException("재직 중인 작성자만 문서를 작성할 수 있습니다.");
		}
		List<String> canonical = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String requested : approvers) {
			userVO candidate = userMapper.selectUserForUpdate(requested);
			if (candidate == null || candidate.isAuthority() || !"재직".equals(candidate.getUser_status())
					|| author.getUsernum().equals(candidate.getUsernum()) || !seen.add(candidate.getUsernum())) {
				throw new IllegalArgumentException("본인·관리자·퇴직·중복 사원은 결재자로 지정할 수 없습니다.");
			}
			canonical.add(candidate.getUsernum());
		}
		return canonical;
	}

	private static List<approval_line_VO> lines(int approvalNo, List<String> approvers) {
		List<approval_line_VO> lines = new ArrayList<>();
		for (int index = 0; index < approvers.size(); index++) {
			approval_line_VO line = new approval_line_VO();
			line.setApproval_no(approvalNo);
			line.setApproval_target(approvers.get(index));
			line.setApproval_status("대기");
			line.setApproval_sort(String.valueOf(index + 1));
			lines.add(line);
		}
		return lines;
	}
}
