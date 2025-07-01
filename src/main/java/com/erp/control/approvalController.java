package com.erp.control;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.erp.dto.approvalDTO;
import com.erp.util.WebUtil;
import com.erp.vo.approvalVO;
import com.erp.vo.approval_file_VO;
import com.erp.vo.approval_line_VO;
import com.erp.vo.userVO;

@Controller
public class approvalController 
{
	@Autowired
	private approvalDTO appdto;
	
	final static String uploadPath = "D:\\sj\\springws\\ERP03\\upload"; //추후 수정
	
	//결재리스트 (대기,반려,진행,승인)
	@RequestMapping(value = "/approval/list.do", method = RequestMethod.GET)
	public String ApprovalList(
			@RequestParam(value = "mode", defaultValue = "1", required = false) String mode,
			@RequestParam(value = "kind", defaultValue = "",  required = false) String kind,
			@RequestParam(value = "status", defaultValue = "", required = false) String status,
			@RequestParam(value = "keyword", defaultValue = "", required = false) String keyword,
			@RequestParam(value = "page", defaultValue = "1") int page, HttpSession session, Model model)
	{
		
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		if(loginUser == null)
		{
			return "redirect:/login/login.do";
		}
		String usernum = loginUser.getUsernum();
		
		approvalVO appVO = new approvalVO();
	    appVO.setKind("문서구분");
	    
	    model.addAttribute("app", appVO);
		
		
		int pageSize = 10;
		int offset   = (page-1) * pageSize;
		// mode에 따라 status 강제 지정
		if (mode != null) 
		{
	        switch (mode) 
	        {
	            case "1": status = "대기중"; break;
	            case "2": status = "반려"; break;
	            case "3": status = "진행중"; break;
	            case "4": status = "승인"; break;
	            default: status = null; break;
	        }
	    }
		
		
		
		Map<String, Object> params = new HashMap<>();
		
		if (!"문서구분".equals(kind)) 
		{
		    params.put("kind", kind);
		}
		params.put("status", status);
		params.put("usernum", usernum);
	    params.put("keyword", keyword);
	    params.put("offset", offset);
	    params.put("limit", pageSize);
		
	    List<approvalVO> approvalList = appdto.selectAppList(params);
	    int totalCount = appdto.countAppList(params);
	    int totalpage  = (int) Math.ceil((double) totalCount / pageSize);
	    
	    int startbk = (page - 1)  - (( page - 1) % 10) + 1;
		int endbk   = startbk + 10 - 1;
		if( endbk > totalpage ) endbk = totalpage;
	    
		model.addAttribute("mode", mode);
		model.addAttribute("approvalList", approvalList);
	    model.addAttribute("page", page);
	    model.addAttribute("startbk", startbk);
	    model.addAttribute("endbk", endbk);
	    model.addAttribute("totalCount", totalCount);
	    model.addAttribute("totalpage", totalpage);
	    model.addAttribute("kind", kind);
	    model.addAttribute("status", status);
	    model.addAttribute("keyword", keyword);
		
		return "approval/list";
	}	
	
	//결재수신
	@RequestMapping(value = "/approval/recv.do", method = RequestMethod.GET)
	public String ApprovalRecv( @RequestParam(value = "kind", defaultValue = "",  required = false) String kind,
								@RequestParam(value = "status", defaultValue = "", required = false) String status,
								@RequestParam(value = "keyword", defaultValue = "", required = false) String keyword,
								@RequestParam(value = "page", defaultValue = "1") int page, 
								HttpSession session, Model model)
	{
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		if(loginUser == null)
		{
			return "redirect:/login/login.do";
		}
		approvalVO appVO = new approvalVO();
		
		int pageSize = 10;
		int offset   = (page-1) * pageSize;
		
		Map<String, Object> params = new HashMap<>();
		
		if (!"문서구분".equals(kind)) 
		{
		    params.put("kind", kind);
		}
		params.put("status", status);
		params.put("kind", kind);
	    params.put("keyword", keyword);
	    params.put("usernum", loginUser.getUsernum());
	    params.put("offset", offset);
	    params.put("limit", pageSize);
		
	    List<approvalVO> approvalList = appdto.selectRecvApprovalList(params);
	    int totalCount = appdto.countRecvApprovalList(params);
	    int totalpage  = (int) Math.ceil((double) totalCount / pageSize);
	    
	    int startbk = (page - 1)  - (( page - 1) % 10) + 1;
		int endbk   = startbk + 10 - 1;
		if( endbk > totalpage ) endbk = totalpage;
	    
		model.addAttribute("approvalList", approvalList);
	    model.addAttribute("page", page);
	    model.addAttribute("startbk", startbk);
	    model.addAttribute("endbk", endbk);
	    model.addAttribute("totalCount", totalCount);
	    model.addAttribute("totalpage", totalpage);
	    model.addAttribute("kind", kind);
	    model.addAttribute("status", status);
	    model.addAttribute("keyword", keyword);
		model.addAttribute("app", appVO);
	    
		return "approval/recv";
	}
	
	//결재작성(기안서 보기)
	@RequestMapping(value = "/approval/view.do", method = RequestMethod.GET)
	public String ApprovalView(@RequestParam(required = true) int approval_no,
								@RequestParam(value = "mode", required = false, defaultValue = "") String mode,
								HttpSession session,Model model)
	{
		userVO loginUser = (userVO)session.getAttribute("loginUser"); 
		
		approvalVO vo = appdto.selectApprovalDetail(approval_no);
		vo.setApproval_content(WebUtil.Text2HTML(vo.getApproval_content()));
		
		appdto.getAppLineStatuses(approval_no);
		
		if("대기중".equals(vo.getDocument_status()))
		{
			mode = "1";
		}else if("반려".equals(vo.getDocument_status()))
		{
			mode = "2";
		}else if("진행중".equals(vo.getDocument_status()))
		{
			mode = "3";
		}else if("승인".equals(vo.getDocument_status()))
		{
			mode = "4";
		}
		
		List<approval_file_VO> fileVO = appdto.selectApprovalFiles(approval_no);
		List<approval_line_VO> lineVO = appdto.selectApprovalLines(approval_no);
		
		
		model.addAttribute("appfileList", fileVO);
		model.addAttribute("item", vo);
		model.addAttribute("addedLine", lineVO);
		model.addAttribute("mode", mode);
		
		
		if (fileVO == null || fileVO.isEmpty()) {
		    System.out.println("첨부파일이 없습니다.");
		} else {
		    System.out.println("첨부파일이 있습니다.");
		}
		
		return "approval/view";
	}
	
	@RequestMapping(value = "/approval/delete.do", method = RequestMethod.GET)
	public String deleteApproval(HttpSession session, @RequestParam int approval_no) 
	{	
		userVO loginUser = (userVO)session.getAttribute("loginUser");

	    approvalVO appVO = new approvalVO();
	    appVO.setApproval_no(approval_no);
	    appVO.setUsernum(loginUser.getUsernum());

	    int result = appdto.deleteApproval(appVO);

	    if (result > 0) 
	    {
	        return "redirect:/approval/list.do";
	    } else 
	    {
	        return "error";
	    }
	}
	
	@RequestMapping(value = "/approval/updateComment.do", method = RequestMethod.POST)
	public String updateApprovalAll(HttpSession session, 
                                  @RequestParam int approval_no, 
                                  @RequestParam String approval_status, 
                                  @RequestParam String comment) 
	{	
	    userVO loginUser = (userVO) session.getAttribute("loginUser");
	    
	    LocalDate date = LocalDate.now();
	    
	    approval_line_VO approvalLine = new approval_line_VO();
	    approvalLine.setApproval_no(approval_no);
	    approvalLine.setApprover(loginUser);
	    approvalLine.setApproval_target(loginUser.getUsernum());
	    approvalLine.setApproval_status(approval_status);
	    approvalLine.setComment(comment);
	    approvalLine.setApproval_date(date.toString());       

	    appdto.upAppLineStatus(approvalLine);
	    
	    approvalVO approvalVO = new approvalVO();
	    approvalVO.setApproval_no(approval_no);
	    approvalVO.setDocument_status(approval_status);
	    
	    appdto.updateApprovalAll(approvalLine, approvalVO);
	    
	    appdto.upDocStatusAuto(approval_no);

	    return "redirect:/approval/view.do?approval_no=" + approval_no;
	   
	}
	
	@RequestMapping(value = "/approval/down.do", method = RequestMethod.GET)
	public void FileDownLoad(@RequestParam(required = true) int no, HttpServletResponse response) throws Exception {

	    // 파일 정보 가져오기
	    List<approval_file_VO> fileList = appdto.selectApprovalFiles(no);
	    if (fileList == null || fileList.isEmpty()) return;

	    approval_file_VO fileVO = fileList.get(0); // 첫 번째 파일 사용

	    File file = new File(uploadPath, fileVO.getAfname());

	    // 파일명 인코딩
	    String encodedFileName = new String(fileVO.getAfname().getBytes("UTF-8"), "ISO-8859-1");

	    
	    response.setContentType("application/download");
	    response.setContentLength((int) file.length());
	    response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");

	    // 파일 스트림 처리
	    try (OutputStream os = response.getOutputStream();
	         FileInputStream fis = new FileInputStream(file)) {
	        FileCopyUtils.copy(fis, os);
	    }
	}
	
	//결재작성
	@RequestMapping(value = "/approval/write.do", method = RequestMethod.GET)
	public String ApprovalWrite(HttpServletRequest request, Model model)
	{	
		
		//로그인 확인
		HttpSession session = request.getSession();

		userVO loginUser = (userVO) session.getAttribute("loginUser");
		
		if(loginUser == null)
		{
			return "redirect:/approval/list.do";
		}
		
		//날짜 자동 입력
		LocalDate date = LocalDate.now();
		
		approvalVO appVO = new approvalVO();
		
		List<userVO> modalList = appdto.lineApproval(loginUser);
		
		appVO.setWritedate(date.toString());
		appVO.setUsernum(loginUser.getUsernum());
		
		model.addAttribute("appVO", appVO);
		model.addAttribute("loginUser",loginUser);
		model.addAttribute("modalList",modalList);
		
		return "approval/write";
	}
	
	//결재작성 (등록)
	@RequestMapping(value = "/approval/write.do", method = RequestMethod.POST)
	public String DoApprovalWrite(approvalVO appVO, approval_line_VO lineVO,
								  @RequestParam("attach")MultipartFile file,
								  @RequestParam(value = "approval_target", required = false) 
								  List<String> approver_target,
								  @RequestParam(value = "approval_sort", required = false) 
								  List<String> approver_sort,
								  HttpServletRequest request,
								  HttpServletResponse response) throws IOException
	{	
		HttpSession session = request.getSession();
		userVO loginUser = (userVO) session.getAttribute("loginUser");

		System.out.println("로그인 결과: " + loginUser.getUsernum());
		appVO.setUsernum(loginUser.getUsernum());
		
		//첨부파일
		List<approval_file_VO> fileList = new ArrayList<>();
		
		if(file != null)
		{
			String originalFileName = file.getOriginalFilename();
			System.out.println("원본 이름 : " + originalFileName);
			
			if(!originalFileName.equals(""))
			{
				System.out.println("파일이 업로드되었음");
				UUID uuid = UUID.randomUUID();
				String savedFileName = uuid.toString();
				
				File newFile = new File(uploadPath + savedFileName);
				
				file.transferTo(newFile);
				
				approval_file_VO newFileVO = new approval_file_VO();
				newFileVO.setAfname(originalFileName);
				newFileVO.setApname(savedFileName);
				
				
				fileList.add(newFileVO);
			}else
			{
				System.out.println("파일이 업로드되지 않았음");
			}
			
		}else
		{
			System.out.println("파일이 업로드되지 않았음");
		}
		
		//결재선 라인 추가
		List<approval_line_VO> lineList = new ArrayList<>();
		if (approver_target != null && !approver_target.isEmpty()) {
			for (String approverUsernum : approver_target) {
				approval_line_VO appLineVO = new approval_line_VO();
				appLineVO.setApproval_no(appVO.getApproval_no());
				appLineVO.setApproval_target(approverUsernum); 
				appLineVO.setApproval_status("대기");
				appLineVO.setApproval_sort(String.valueOf(lineList.size() + 1));
				lineList.add(appLineVO);
			}
		}
		
		
		appdto.insertApproval(appVO, fileList, lineList);
		
		int app_no = appVO.getApproval_no();
		return "redirect:/approval/view.do?approval_no=" + app_no;
	}
	
	
	//결재수정
	@RequestMapping(value = "/approval/modify.do", method = RequestMethod.GET)
	public String ApprovalModify(@RequestParam(required = true) int approval_no,HttpSession session,Model model)
	{
		userVO loginUser = (userVO) session.getAttribute("loginUser");

	    if (loginUser == null) {
	        return "redirect:/approval/list.do";
	    }

	    LocalDate date = LocalDate.now();
	    
	    approvalVO vo = appdto.selectApprovalDetail(approval_no);
	    List<approval_file_VO> fileVO = appdto.selectApprovalFiles(approval_no);
	    List<approval_line_VO> lineVO = appdto.selectApprovalLines(approval_no);
	    List<userVO> modalList = appdto.lineApproval(loginUser);
	    
	    vo.setWritedate(date.toString());
	    vo.setUsernum(loginUser.getUsernum());
	    
	    model.addAttribute("item", vo);
	    model.addAttribute("appfileList", fileVO);
	    model.addAttribute("addedLine", lineVO);
	    model.addAttribute("loginUser", loginUser);
	    model.addAttribute("modalList", modalList);
	    
	    if (fileVO == null || fileVO.isEmpty()) {
		    System.out.println("첨부파일이 없습니다.");
		} else {
		    System.out.println("첨부파일이 있습니다.");
		}

	    
		return "approval/modify";
	}
	
	//결재수정 (POST)
	@RequestMapping(value = "/approval/modify.do", method = RequestMethod.POST)
	public String ApprovalDoModify(approvalVO appVO,approval_file_VO fileVO,
						            @RequestParam("approval_no")int approval_no,
						            @RequestParam("attach") MultipartFile file,
						            @RequestParam(value = "approval_target", required = false) List<String> approver_target,
						            HttpServletRequest request) throws IOException
	{
		
		List<approval_file_VO> fileList = null;
		
		if(file != null)
		{			
			String originalFileName = file.getOriginalFilename();
			System.out.println("originalFileName:" + originalFileName);
			
			//파일 이름이 중복되지 않도록 파일 이름 변경 : 서버에 저장할 이름
			// UUID 클래스 사용
			UUID uuid = UUID.randomUUID();
			String savedFileName = uuid.toString();
			
			//파일 생성
			File newFile = new File(uploadPath + savedFileName);
			
			//서버로 전송
			file.transferTo(newFile);
			
			fileVO.setAfname(originalFileName);
			fileVO.setApname(savedFileName);
			
			fileList = new ArrayList<>();
			fileList.add(fileVO);
		}else
		{
			System.out.println("File not upload..");
		}

	    // 결재선 리스트 준비
	    List<approval_line_VO> lineList = new ArrayList<>();
	    if (approver_target != null && !approver_target.isEmpty()) {
	        for (int i = 0; i < approver_target.size(); i++) {
	            approval_line_VO lineVO = new approval_line_VO();
	            lineVO.setApproval_no(approval_no);
	            lineVO.setApproval_target(approver_target.get(i));
	            lineVO.setApproval_sort(String.valueOf(i + 1));
	            lineVO.setApproval_status("대기");
	            lineList.add(lineVO);
	        }
	    }
	    
	    appVO.setApproval_no(approval_no);
	    appdto.modifyApproval(appVO, fileList, lineList, approval_no);
	    
	    return "redirect:/approval/view.do?approval_no=" + approval_no;
	}
	
	//전제 승인 내역
	@RequestMapping(value = "/approval/allok.do", method = RequestMethod.GET)
	public String ApprovalAllok(@RequestParam(required = false ,defaultValue = "") String kind,
					            @RequestParam(required = false ,defaultValue = "") String keyword,
					            @RequestParam(required = false ,defaultValue = "") String team,
					            @RequestParam(defaultValue = "1") int page,
					            Model model)
	{
		
		int pageSize = 10;
	    int offset = (page - 1) * pageSize;

	    Map<String, Object> params = new HashMap<>();
	    if (!"문서구분".equals(kind)) {
	    	params.put("kind", kind);
	    }
	    if (!"부서".equals(team)) {
	    	params.put("team", team);
	    }
	    params.put("keyword", keyword);
	    params.put("offset", offset);
	    params.put("limit", pageSize);
		
	    System.out.println("팀 필터: " + team);
	    
	    List<approvalVO> list = appdto.selectListAll(params);
	    int totalCount = appdto.selectListAllCount(params);
	    
	    int totalpage = (int) Math.ceil((double) totalCount / pageSize);
	    int startbk = (page - 1) - ((page - 1) % 10) + 1;
	    int endbk = startbk + 10 - 1;
	    if (endbk > totalpage) endbk = totalpage;
	    
	    model.addAttribute("ListAll", list);
	    model.addAttribute("page", page);
	    model.addAttribute("startbk", startbk);
	    model.addAttribute("endbk", endbk);
	    model.addAttribute("totalCount", totalCount);
	    model.addAttribute("totalpage", totalpage);
	    model.addAttribute("kind", kind);
	    model.addAttribute("keyword", keyword);
	    model.addAttribute("team", team);
	    
	    if (list.isEmpty()) {
	        System.out.println("필터링된 팀에 해당하는 문서가 없습니다.");
	    }
	    
		return "approval/allok";
	}
}
