package com.erp.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.userVO;

@Mapper
public interface ApprovalMapper {

	List<userVO> lineApproval(userVO loginUser);

	int insertApproval(approvalVO approval);

	int insertApprovalFiles(List<approval_file_VO> files);

	int insertApprovalLines(List<approval_line_VO> lines);

	approvalVO selectApprovalDetail(int approvalNo);

	approvalVO selectApprovalDetailForUser(
			@Param("approvalNo") int approvalNo, @Param("usernum") String usernum);

	List<approval_file_VO> selectApprovalFiles(int approvalNo);

	List<approval_line_VO> selectApprovalLines(int approvalNo);

	int updateApprovalLineStatus(approval_line_VO line);

	int updateDocumentStatusAuto(@Param("approvalNo") int approvalNo);

	int updateApproval(@Param("approval") approvalVO approval, @Param("usernum") String usernum);

	int deleteApproval(@Param("approvalNo") int approvalNo, @Param("usernum") String usernum);

	int deleteApprovalFiles(int approvalNo);

	int deleteApprovalLines(int approvalNo);

	List<approvalVO> selectAppList(Map<String, Object> params);

	int countAppList(Map<String, Object> params);

	List<approvalVO> selectRecvApprovalList(Map<String, Object> params);

	int countRecvApprovalList(Map<String, Object> params);

	List<approvalVO> selectListAll(Map<String, Object> params);

	int selectListAllCount(Map<String, Object> params);
}
