package com.erp.control;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.erp.dto.approvalDTO;
import com.erp.dto.noticeDTO;
import com.erp.dto.userDTO;
import com.erp.vo.noticeVO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Controller
public class loginController
{
	@Autowired
	userDTO userdto;
	
	@Autowired
	noticeDTO noticedto;
	
	@Autowired
	private approvalDTO appdto;	
	
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String Login()
	{
		return "redirect:/login/login.do";
	}
	
	@RequestMapping(value = "/login/login.do", method = RequestMethod.GET)
    public String showLogin(HttpServletRequest request) {
		
		HttpSession session = request.getSession();
		session.setAttribute("loginUser", null);
        return "login/login";
    }
	
	//로그인 로직
	@RequestMapping(value = "/login/login.do", method = RequestMethod.POST)
	@ResponseBody
	public String DoLogin(userVO inputUser, HttpSession session, Model model) 
	{
		System.out.println("입력한 아이디: " + inputUser.getUsernum());
	    System.out.println("입력한 비밀번호: " + inputUser.getUserpw());
		
		userVO loginUser = userdto.login(inputUser.getUsernum(), inputUser.getUserpw());

		if (loginUser == null) {
			// 로그인 실패
			//model.addAttribute("loginError", "아이디 또는 비밀번호가 올바르지 않습니다.");
			//return "login/login";
			return "ERROR";
		}
		
		System.out.println("로그인 결과: " + loginUser.getUsernum());
		
		// 로그인 성공 → 세션에 저장
		session.setAttribute("loginUser", loginUser);
		System.out.println(loginUser.isFirstlogin());
		// 첫 로그인인 경우 비밀번호 변경 페이지로 리다이렉트
		if (loginUser.isFirstlogin()) {
			//return "redirect:/login/changepw.do";
			return "CHANGE";
		} else {
			//return "redirect:/main.do";
			return "OK";
		}
		
		
	}
	
	//비밀번호 변경 페이지
	@RequestMapping(value = "/login/changepw.do", method = RequestMethod.GET)
	public String changePwPage(HttpServletRequest request) {
		
		//로그인되었는지 검사한다. (세션에서 저장된 값 불러와서 null이면 로그인화면으로 이동, 아니면 비밀번호 변경 화면으로 이동)
		HttpSession session = request.getSession();
		userVO loginUser = (userVO) session.getAttribute("loginUser");
		if(loginUser == null) {
			return "login/login";
		}
		return "login/changepw";
	}
	
	
	//처음 로그인 한 경우 비밀번호 변경 로직
	@RequestMapping(value = "/login/changepw.do", method = RequestMethod.POST)
	@ResponseBody
	public String DoChangePw(HttpServletRequest request, Model model) {
		
		HttpSession session = request.getSession();
		userVO loginUser = (userVO) session.getAttribute("loginUser");
		
		String oldpw = request.getParameter("oldpw");
		String newpw = request.getParameter("newpw");
		
		
		//기존 비밀번호 일치하는지 검사
		userVO vo = userdto.login(loginUser.getUsernum(), oldpw);
		if(vo == null)
		{
			//기존 비밀번호가 일치하지 않음
			return "ERROR";			
		}
		
		//비밀번호 변경이 실패했을 경우
		if(!(userdto.changePassword(loginUser.getUsernum(), newpw))) {
			model.addAttribute("pwError", "비밀번호 변경 실패");
			return "ERROR";	
		}
		
		loginUser.setFirstlogin(false);
		loginUser.setUserpw(newpw);
		session.setAttribute("loginUser", loginUser);
		
		
		return "OK";
	}
	
	//메인 화면
	@RequestMapping(value = "/main.do", method = RequestMethod.GET)
	public String Main(HttpSession session,Model model)
	{     
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		if(loginUser == null)
		{
			return "redirect:/login/login.do";
		}

		
        //공지사항 목록
		List<noticeVO> list = noticedto.mainList();
		System.out.println("list size:" + list.size());
		for(noticeVO n : list)
		{
			System.out.println(n.getNotice_title());
		}
		
		model.addAttribute("list", list);
		
		return "main";
		
	}
	
	//결재대기/결재반려/결재진행/결재승인/결재수신 껀에 대한 건수 리턴
	@RequestMapping(value = "/approval/count.do", method = RequestMethod.GET)
	@ResponseBody
	public String GetCount(HttpSession session)
	{
		int totalCount;
		String msg = "";
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		String usernum = loginUser.getUsernum();

		Map<String, Object> params = new HashMap<>();
		params.put("status", "대기중");
		params.put("kind", "");
		params.put("usernum", usernum);
	    params.put("keyword", "");
	    params.put("offset", 0);
	    params.put("limit", 0);
	    totalCount = appdto.countAppList(params);
	    System.out.println("대기중:" + totalCount);
	    msg += totalCount + "/";
	    
	    params.put("status", "반려");
	    totalCount = appdto.countAppList(params);
	    System.out.println("반려:" + totalCount);	 
	    msg += totalCount + "/";
	    
	    params.put("status", "진행중");
	    totalCount = appdto.countAppList(params);
	    System.out.println("진행중:" + totalCount);	
	    msg += totalCount + "/";
	    
	    params.put("status", "승인");
	    totalCount = appdto.countAppList(params);
	    System.out.println("승인:" + totalCount);	
	    msg += totalCount + "/";
	    
	    params.put("status", "");
	    totalCount = appdto.countRecvApprovalList(params);
	    System.out.println("수신:" + totalCount);	
	    msg += totalCount;
	    
		return msg;
	}
	
}
