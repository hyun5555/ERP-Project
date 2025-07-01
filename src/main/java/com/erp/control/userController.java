package com.erp.control;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.erp.dto.userDTO;
import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Controller
public class userController 
{
	@Autowired
	userDTO userdto;
	
	
	// 사원관리 - 전체 사원 목록 조회
	@RequestMapping(value = "/user/list.do", method = RequestMethod.GET)
	public String UserList(@ModelAttribute("searchVO") searchVO vo, Model model) {

		// 전체 사원 목록 조회
		List<userVO> userList = userdto.getAllUserList(vo);
		
		// 전체 카운트
		int total = userdto.getTotalCount(vo);
		
		// 총 페이지 계산
		int totalPages = (int) Math.ceil((double) total / 10.0);

		// 결과를 모델에 담아 전달
		model.addAttribute("userList", userList);
		model.addAttribute("pageno", vo.getPageno());
		model.addAttribute("totalPages", totalPages);
		// 검색 조건도 같이 전달 (JSP에서 입력값 유지 용도)
		model.addAttribute("searchVO", vo);

		return "user/list";
	}
		
	//모달창 구성원 목록 조회
	@RequestMapping(value = "/user/modal.do", method = RequestMethod.GET)
	public String UserModalList(Model model) {
		
	    // 조건 없이 전체 사원 목록 가져오기 (정렬 포함)
	    List<userVO> userList = userdto.getModalList(new userVO());

	    // 사원 목록을 모델에 담아서 JSP로 전달
	    model.addAttribute("userList", userList);

	    return "user/modal"; // user/modal.jsp에 모달창 구성
	}

	
	//사원 세부정보
	@RequestMapping(value = "/user/view.do", method = RequestMethod.GET)
	public String UserView(@RequestParam(required = true) String usernum, Model model)
	{
		userVO vo = userdto.getUserInfo(usernum);
		
		model.addAttribute("user", vo);
		
		return "user/view";
	}
	
	//사원수정
	@RequestMapping(value = "/user/modify.do", method = RequestMethod.GET)
	public String UserModify(@RequestParam(required = true) String usernum, Model model)
	{
		userVO vo = userdto.getUserInfo(usernum);
		
		// 전화번호 나누기
		String phonenum = vo.getPhonenum();
		String phonenum1 = "", phonenum2 = "", phonenum3 = "";
		if (phonenum != null && phonenum.contains("-")) {
			String[] p = phonenum.split("-");
			if (p.length == 3) {
				phonenum1 = p[0]; phonenum2 = p[1]; phonenum3 = p[2];
			}
		}

		// 내선번호 나누기
		String officenum = vo.getOfficenum();
		String officenum1 = "", officenum2 = "", officenum3 = "";
		if (officenum != null && officenum.contains("-")) {
			String[] o = officenum.split("-");
			if (o.length == 3) {
				officenum1 = o[0]; officenum2 = o[1]; officenum3 = o[2];
			}
		}

		// 이메일 나누기
		String email = vo.getEmail();
		String email1 = "", email2 = "";
		if (email != null && email.contains("@")) {
			String[] e = email.split("@");
			if (e.length == 2) {
				email1 = e[0]; email2 = e[1];
			}
		}
		
		model.addAttribute("user", vo);
		model.addAttribute("phonenum1", phonenum1);
		model.addAttribute("phonenum2", phonenum2);
		model.addAttribute("phonenum3", phonenum3);
		model.addAttribute("officenum1", officenum1);
		model.addAttribute("officenum2", officenum2);
		model.addAttribute("officenum3", officenum3);
		model.addAttribute("email1", email1);
		model.addAttribute("email2", email2);
		
		
		return "user/modify";
	}
	
	//사원 수정 처리 로직(POST) 
	@RequestMapping(value = "/user/modify.do", method = RequestMethod.POST)
	public String DoUserModify(HttpServletRequest request, userVO vo, Model model)
	{
		HttpSession session = request.getSession(); 
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		
		//관리자 또는 본인 정보가 아니면 사원 수정 실패
		if(loginUser == null || !(loginUser.isAuthority()) && !loginUser.getUsernum().equals(vo.getUsernum()) ) 
		{
			model.addAttribute("error", "관리자만 사원을 수정할 수 있습니다");
			return "user/modify";
		}
		
		String phone = request.getParameter("phonenum1") + "-" +
	               request.getParameter("phonenum2") + "-" +
	               request.getParameter("phonenum3");
		vo.setPhonenum(phone);

		String office = request.getParameter("officenum1") + "-" +
                request.getParameter("officenum2") + "-" +
                request.getParameter("officenum3");
		
		vo.setOfficenum(office);
		
		String email = request.getParameter("email1") + "@" + request.getParameter("email2");
		vo.setEmail(email);
		
		vo.setTeam(request.getParameter("team")); // Buseo → team
		vo.setLevel(request.getParameter("level")); // jikgeup → level
		vo.setUser_status(request.getParameter("user_status")); // sangtae → user_status

		System.out.println("vo.isAuthority():" + vo.isAuthority());
		System.out.println("vo.getUserpw():" + vo.getUserpw());
		
		// 사원 등록 수행
	    boolean result = userdto.updateUserInfo(vo);
	    if (!result) {
	        model.addAttribute("error", "사원 수정에 실패했습니다.");
	        return "user/modify";
	    }
		
	    return "redirect:/user/view.do?usernum=" + vo.getUsernum();
	}
	
	//사원등록
	@RequestMapping(value = "/user/write.do", method = RequestMethod.GET)
	public String UserWrite(HttpServletRequest request,Model model)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
		Date now = new Date();
		String year = sdf.format(now);
		String usernum = year;
		
		for(int i = 1; i <= 100; i++)
		{
			usernum = String.format("%s%03d",year,i);
			System.out.println("usernum:" + usernum);
			if( userdto.FindUsernum(usernum) <= 0 )
			{
				break;
			}
		}
		
		
		model.addAttribute("usernum",usernum);
		
		return "user/write";
	}
	
	//사원등록 처리 로직 (POST)
	@RequestMapping(value = "/user/write.do", method = RequestMethod.POST)
	public String DoUserWrite(HttpServletRequest request, userVO vo, Model model)
	{
		HttpSession session = request.getSession(); 
		userVO loginUser = (userVO)session.getAttribute("loginUser");
		
		//관리자가 아니면 사원 등록 실패
		if(loginUser == null || !(loginUser.isAuthority())) {
			model.addAttribute("error", "관리자만 사원을 등록할 수 있습니다");
			return "user/write";
		}
		
		// 사원 등록 수행
	    boolean result = userdto.insertUser(loginUser, vo);
	    if (!result) {
	        model.addAttribute("error", "사원 등록에 실패했습니다.");
	        return "user/write";
	    }
		
		return "redirect:/user/list.do";
	}
	
	@RequestMapping(value = "/user/delete.do", method = RequestMethod.GET)
	public String deleteUser(@RequestParam("usernum") String usernum,
	                         HttpServletRequest request,
	                         Model model) {
	    HttpSession session = request.getSession();
	    userVO loginUser = (userVO) session.getAttribute("loginUser");

	    if (loginUser == null || !loginUser.isAuthority()) {
	        model.addAttribute("error", "관리자만 사원을 삭제할 수 있습니다.");
	        model.addAttribute("redirectUrl", "/user/list.do");
	        return "alert";  // JSP에서 알림 처리
	    }

	    boolean result = userdto.userDelete(usernum);
	    if (!result) {
	        model.addAttribute("error", "사원 삭제에 실패했습니다.");
	        model.addAttribute("redirectUrl", "/user/list.do");
	        return "alert";
	    }

	    return "redirect:/user/list.do";
	}

	
	//내 정보
	@RequestMapping(value = "/user/myinfo.do", method = RequestMethod.GET)
	public String UserMyinfo(@RequestParam(required = true) String usernum, Model model)
	{
		userVO vo = userdto.getUserInfo(usernum);
		
		model.addAttribute("user", vo);
		
		return "user/myinfo";
	}
	
	// 일괄삭제 처리
	@RequestMapping(value = "/user/deleteSelected.do", method = RequestMethod.POST)
	public String deleteSelected(@RequestParam("usernums") List<String> usernums,
	                             HttpServletRequest request,
	                             Model model) {
	    HttpSession session = request.getSession();
	    userVO loginUser = (userVO) session.getAttribute("loginUser");

	    if (loginUser == null || !loginUser.isAuthority()) {
	        model.addAttribute("error", "관리자만 일괄삭제할 수 있습니다.");
	        model.addAttribute("redirectUrl", "/user/list.do");
	        return "alert";
	    }

	    for (String usernum : usernums) {
	        userdto.userDelete(usernum);
	    }

	    return "redirect:/user/list.do";
	}
	
}
