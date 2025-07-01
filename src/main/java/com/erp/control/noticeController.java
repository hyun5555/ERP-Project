package com.erp.control;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.erp.dto.noticeDTO;
import com.erp.vo.noticeVO;
import com.erp.vo.notice_teamVO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Controller
public class noticeController 
{
	@Autowired
	noticeDTO noticedto;
	
	final static String uploadPath = "D:\\workspace_spring\\ERP03\\upload";
	//final static String uploadPath = "D:\\ohm\\workspace\\spring\\SpringEx07\\upload";
	
	//공지사항 리스트
	@RequestMapping(value = "/notice/list.do", method = RequestMethod.GET)
	public String NoticeList(@RequestParam(defaultValue = "1") int page, 
			@RequestParam(required = false) String notice_team,
			@RequestParam(required = false) String searchWord,
			Model model)
	{
		searchVO search = new searchVO();
		search.setPageno(page);
        search.setSearchWord(searchWord);
        search.setNotice_team(notice_team);
        
        //전체 공지사항 갯수
        int total = noticedto.GetTotal(search);
        
		//최대 페이징
		int maxpage = total / 10;
		if( total % 10 != 0) maxpage++;
		
		//시작 블럭
		int startbk = (page - 1)  - (( page - 1) % 10) + 1;
		int endbk   = startbk + 10 - 1;
		if( endbk > maxpage ) endbk = maxpage;
        
        //공지사항 목록
		List<noticeVO> list = noticedto.noticeList(search);
		
		model.addAttribute("total", total);
		model.addAttribute("currpage", page);
		model.addAttribute("maxpage", maxpage);	
		model.addAttribute("startbk", startbk);
		model.addAttribute("endbk", endbk);
		model.addAttribute("list", list);
		model.addAttribute("notice_team", notice_team);
		model.addAttribute("searchWord", searchWord);
		
		return "notice/list";
	}
	
	//공지사항 상세보기
	@RequestMapping(value = "/notice/view.do", method = RequestMethod.GET)
	public String NoticeView(@RequestParam("notice_no") int notice_no, Model model)
	{
		noticeVO vo = noticedto.getNoticeInfo(notice_no);
		vo.PrintInfo();
		
		vo.setNotice_content(com.erp.util.WebUtil.Text2HTML(vo.getNotice_content()));
		
		List<notice_teamVO> team_vo = noticedto.getNoticeTeam(notice_no);
		
		model.addAttribute("notice", vo);
		model.addAttribute("team", team_vo);
		
		return "notice/view";
	}
	
	//첨부파일 다운로드
	@RequestMapping(value = "/notice/down.do", method = RequestMethod.GET)
	public void DownLoad(@RequestParam("notice_no") int notice_no, HttpServletResponse response) throws Exception
	{
		noticeVO vo = noticedto.getNoticeInfo(notice_no);
		
		File file = new File(uploadPath, vo.getPname());
		// 파일명 인코딩
		String encodedFileName = new String (vo.getFname().getBytes("UTF-8"), "ISO-8859-1");

		// file 다운로드 설정
		response.setContentType("application/download");
		response.setContentLength((int)file.length());
		response.setHeader("Content-Disposition", "attatchment;filename=\"" + encodedFileName + "\"");
		
		// 다운로드 시 저장되는 이름은 Response Header의 "Content-Disposition"에 명시
		OutputStream os = response.getOutputStream();
		
		FileInputStream fis = new FileInputStream(file);
		FileCopyUtils.copy(fis, os);
		
		// fis.close();
		// os.close();
	}
	
	//공지사항 등록 페이지 이동
	@RequestMapping(value = "/notice/write.do", method = RequestMethod.GET)
	public String NoticeWrite(HttpSession session, Model model)
	{
		
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		if(loginUser == null || !(loginUser.isAuthority())) {
			model.addAttribute("error", "관리자만 공지사항을 등록할 수 있습니다");
			return "noitce/list";
		}
		
		return "notice/write";
	}
	
	//공지사항 등록완료 (POST)
	@RequestMapping(value = "/notice/writeOK.do", method = RequestMethod.POST)
	public String DoNoticeWrite(HttpServletRequest request,
			noticeVO vo, 
			String[] notice_team,
			@RequestParam("fileInput") MultipartFile file,
			Model model) throws IOException 
	{

	    HttpSession session = request.getSession(); 
	    userVO loginUser = (userVO) session.getAttribute("loginUser");

	    // 관리자가 아니면 공지사항 등록 실패
	    if (loginUser == null || !loginUser.isAuthority()) {
	        model.addAttribute("error", "관리자만 공지사항을 등록할 수 있습니다");
	        return "redirect:/notice/list.do";
	    }

	    // 파일 업로드 처리
	    if (file != null && !file.isEmpty()) {
	        String originalFileName = file.getOriginalFilename();
	        UUID uuid = UUID.randomUUID();
	        String savedFileName = uuid.toString();

	        // 서버에 파일 저장
	        File newFile = new File(uploadPath + "\\" + savedFileName);
	        file.transferTo(newFile);

	        vo.setFname(originalFileName);
	        vo.setPname(savedFileName);
	    } else {
	        System.out.println("File not upload..");
	    }
	    vo.setUsernum(loginUser.getUsernum());

	    //공지대상
	    /*
	    for(String team : notice_team)
	    {
	    	System.out.println(team);
	    }
	    */
	    
	    vo.PrintInfo();
	    // DB에 등록
	    noticedto.noticeInsert(vo,notice_team);
	    
	    /*
	    //각 부서별로 공지사항 일괄등록하는 예제
	    for(int i = 1; i < 100; i++)
	    {
	    	String title = String.format("%03d 번째 개발팀 공지사항입니다.",i);
	    	vo.setNotice_title(title);
	    	String[] team = { "100" };
	    	noticedto.noticeInsert(vo,team);
	    	//noticedto.noticeInsert(vo,notice_team);
	    }

	    for(int i = 1; i < 100; i++)
	    {
	    	String title = String.format("%03d 번째 디자인 공지사항입니다.",i);
	    	vo.setNotice_title(title);
	    	String[] team = { "200" };
	    	noticedto.noticeInsert(vo,team);
	    	//noticedto.noticeInsert(vo,notice_team);
	    }
	    
	    for(int i = 1; i < 100; i++)
	    {
	    	String title = String.format("%03d 번째 경영지원 공지사항입니다.",i);
	    	vo.setNotice_title(title);
	    	String[] team = { "300" };
	    	noticedto.noticeInsert(vo,team);
	    	//noticedto.noticeInsert(vo,notice_team);
	    }
	    
	    for(int i = 1; i < 100; i++)
	    {
	    	String title = String.format("%03d 번째 모든 팀 공지사항입니다.",i);
	    	vo.setNotice_title(title);
	    	String[] team = { "100", "200", "300" };
	    	noticedto.noticeInsert(vo,team);
	    	//noticedto.noticeInsert(vo,notice_team);
	    }	
	    */

	    return "redirect:/notice/view.do?notice_no=" + vo.getNotice_no();
	}
	
	//공지사항 삭제
	@RequestMapping(value = "/notice/delete.do", method = RequestMethod.GET)
	public String NoticeDelete(@RequestParam("notice_no") int notice_no, HttpServletRequest request) {
		System.out.println("notice_no:" + notice_no);
	    if (request.getSession().getAttribute("loginUser") == null) 
	    {
	        return "redirect:/notice/list.do";
	    }

	    noticedto.noticeDelete(notice_no);
	    return "redirect:/notice/list.do";
	}
}
