package com.erp.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.userVO;

@Service
@Repository
public class approvalDTO 
{
	@Autowired
	private SqlSession session;
	
	private final static String namespace = "com.erp.approval";
	
	//1. 결재 문서 등록(insert)
	@Transactional
	public int insertApproval(approvalVO approvalVO,
			List<approval_file_VO> fileList, 
			List<approval_line_VO> lineList)
	{
		
		session.insert(namespace + ".insertApproval", approvalVO);
		
		int approvalNo = approvalVO.getApproval_no();
		
	    if (fileList != null && !fileList.isEmpty()) {
	        for (approval_file_VO file : fileList) {
	            file.setApproval_no(approvalNo);
	        }
	        session.insert(namespace + ".insertApprovalFiles", fileList);
	    }

	    if (lineList != null && !lineList.isEmpty()) {
	        for (approval_line_VO line : lineList) {
	            line.setApproval_no(approvalNo);
	        }
	        session.insert(namespace + ".insertApprovalLines", lineList);
	    }
	    return approvalNo;
	}

	//2. 결재 문서 조회 (View)
	//결재 문서 상세 정보 조회
	public approvalVO selectApprovalDetail(int approval_no)
	{
		return session.selectOne(namespace + ".selectApprovalDetail", approval_no);
	}

	//특정 결재 문서의 첨부파일 목록 조회
	public List<approval_file_VO> selectApprovalFiles(int approval_no)
	{
	    return session.selectList(namespace + ".selectApprovalFiles", approval_no);
	}

	//특정 결재 문서의 결재선 목록 조회
	public List<approval_line_VO> selectApprovalLines(int approval_no)
	{
	    return session.selectList(namespace + ".selectApprovalLines", approval_no);
	}
	
	// 결재선으로 추가 가능한 사용자 목록 조회
	public List<userVO> lineApproval(userVO loginUser) {
	    return session.selectList(namespace + ".lineApproval", loginUser);
	}

	//3. 결재 처리 (View에서 승인/반려 시) 관련
	
	//3.1 코맨트, 승인/반려 선택
	
	@Transactional
	public void updateApprovalAll(approval_line_VO approvalLineVO, approvalVO approvalVO)
	{
	    // 결재 라인 상태 업데이트
	    upAppLineStatus(approvalLineVO);

	    // 문서 상태 업데이트
	    upDocStatus(approvalVO);

	    // 문서 상태 자동 업데이트 
	    upDocStatusAuto(approvalVO.getApproval_no());
	}
	
	public void upAppLineStatus(approval_line_VO approvalLineVO)
	{
		session.update(namespace + ".updateApprovalLineStatus", approvalLineVO);
	}

	public void upDocStatus(approvalVO approvalVO)
	{
		session.update(namespace + ".updateDocumentStatus", approvalVO);
	}

	public List<approval_line_VO> getAppLineStatuses(int approval_no)
	{
	    return session.selectList(namespace + ".getApprovalLineStatuses", approval_no);
	}

	public void upDocStatusAuto(int approval_no)
	{
		session.update(namespace + ".updateDocumentStatusAuto", approval_no);
	}
	
	//4. 결재 문서 수정 ('대기중' 상태일 때) 관련
	@Transactional
	public int deleteApproval(approvalVO appVO) 
	{

		int appno = appVO.getApproval_no();
		String usernum = appVO.getUsernum(); 
		
		Map<String, Object> map = new HashMap<>();
		map.put("approval_no", appno);
		map.put("usernum", usernum);
		
		// 삭제 전에 상태 및 사용자 확인
		int deletedCount = session.delete(namespace + ".deleteApproval", map);
		
		if (deletedCount > 0) {
		session.delete(namespace + ".deleteApprovalFiles", appno);
		session.delete(namespace + ".deleteApprovalLines", appno);
		}
		
		return deletedCount;
	}
	
	@Transactional
	public void modifyApproval(approvalVO appVO, List<approval_file_VO> fileList,List<approval_line_VO> lineList,int approval_no)
	{
		System.out.println("modifyApproval 01");
		session.update(namespace + ".updateApproval", appVO);
		System.out.println("modifyApproval 02");
		session.update(namespace + ".updateApprovalStatusToPending", approval_no);
		System.out.println("modifyApproval 03");
		if(fileList != null)
		{
			session.update(namespace + ".modifyApprovalFiles", fileList);
		}
		System.out.println("modifyApproval 04");
		session.insert(namespace + ".modifyApprovalLines", lineList);
		System.out.println("modifyApproval 05");
		
	}

	//5. 결재 문서 목록 (List) 관련
	public List<approvalVO> selectAppList(Map<String, Object> params) //검색 포함
	{
	    return session.selectList(namespace + ".selectAppList", params);
	}

	public int countAppList(Map<String, Object> params) {
	    return session.selectOne(namespace + ".countAppList", params);
	}
	
	//결재수신 관련
	public List<approvalVO> selectRecvApprovalList(Map<String, Object> params) {
	    return session.selectList(namespace + ".selectRecvApprovalList", params);
	}

	public int countRecvApprovalList(Map<String, Object> params) {
	    return session.selectOne(namespace + ".countRecvApprovalList", params);
	}
	//전체 승인내역
	public List<approvalVO> selectListAll(Map<String, Object> params) //검색 포함
	{
	    return session.selectList(namespace + ".selectListAll", params);
	}
	public int selectListAllCount(Map<String, Object> params) {
	    return session.selectOne(namespace + ".selectListAllCount", params);
	}
	
}
