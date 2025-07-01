<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "user-edit");
%>    
<%@ include file="../include/header.jsp" %>

<script>
window.onload = function()    
{
	$("#name").focus();
	
	$("#usernum").keyup(function(){
		DupCheckID();      //usernum 키보드입력시, 중복체크 콜백함수(DupCheckID) 실행
	});
	
	$("#writeCancel").click(function(){
		if(confirm("등록취소 취소하시겠습니까?") == true)
		{
			location.href = "<%=request.getContextPath()%>/user/list.do";	
		}
	});
}

let selectedAuthority = null;

$(document).ready(function () {
  $("input[name='authority']").click(function () {
    if (selectedAuthority === this) {
      // 이미 선택된 라디오 버튼을 다시 클릭한 경우 해제
      $(this).prop("checked", false);
      selectedAuthority = null;
    } else {
      // 새 라디오 버튼 선택
      selectedAuthority = this;
    }
  });
});

function DoInsert()
{
	if( $("#name").val() == "" )
	{
		alert("사원명을 입력하세요.");
		$("#name").focus();
		return;
	}	
	if( $("#idnum1").val() == "" & $("#idnum1").val() == "" )
	{
		alert("주민번호를 입력하세요.");
		$("#idnum1").focus();
		return;
	}
	
	
	if( $("#phonenum1").val() == "" & $("#phonenum2").val() == "" & $("#phonenum3").val() == "" )
	{
		alert("전화번호를 입력하세요.");
		$("#phonenum1").focus();
		return;
	}	
	if( $("#phonenum1").val() != "" & $("#phonenum2").val() != "" & $("#phonenum3").val() != "" )
	{
		   var phonenum1 = $("#phonenum1").val();
		   var phonenum2 = $("#phonenum2").val();
		   var phonenum3 = $("#phonenum3").val();
		   $("#phonenum").val(phonenum1 + "-" + phonenum2 + "-" + phonenum3);
	}
	
	
	if( $("#officenum1").val() == "" & $("#officenum2").val() == "" & $("#officenum3").val() == "")
	{
		alert("내선번호를 입력하세요.");
		$("#officenum1").focus();
		return;
	}	
	if( $("#officenum1").val() != "" & $("#officenum2").val() != "" & $("#officenum3").val() != "")
	{
		var officenum1= $("#officenum1").val();
	    var officenum2= $("#officenum2").val();
		var officenum3= $("#officenum3").val();
		$("#officenum").val(officenum1 + "-" + officenum2 + "-" + officenum3);
	}
	
	
	if( $("#email1").val() == "" & $("#email2").val() == "" )
	{
		alert("메일주소를 입력하세요.");
		$("#email1").focus();
		return;
	}	
	if( $("#email1").val() != "" & $("#email2").val() != "" )
	{
		var email1 = $("#email1").val();
		var email2 = $("#email2").val();
		$("#email").val(email1 + "@" + email2 );
	}
	
	
	if( $("#team").val() == "" )
	{
		alert("부서를 입력하세요.");
		$("#team").focus();
		return;
	}	
	if( $("#level").val() == "" )
	{
		alert("직급을 입력하세요.");
		$("#level").focus();
		return;
	}	
	if( $("#user_status").val() == "" )
	{
		alert("근무상태을 입력하세요.");
		$("#user_status").focus();
		return;
	}
	if( $("#usernum").val() == "" )
	{
		alert("사원번호를 입력하세요.");
		$("#usernum").focus();
		return;
	}
	if( $("#userpw").val() == "" )
	{
		alert("비밀번호를 입력하세요.");
		$("#userpw").focus();
		return;
	}
	
	
	document.forms[0].submit();                      
	
}
</script>
<!-------------------------------------------------------- 사원세부정보 등록하기 시작 ----------------------------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
			<div class="card-title mb-1 p-3">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">사원관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; 사원세부정보(등록)</h4>
				</div>
			</div>
				<hr>
			<div class="cteam_header_row">
				<h3>사원세부정보</h3>
			</div>
				<div class="cteam_header_row cteam_button_group"></div>
				<form action="/ERP/user/write.do" method="post">
					<div class="cteam_middle_row">
						<div class="cteam_view_filters">
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label01">사원명</label> <input type="text" name="name" id="name"
									class="user_documentNum" />
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label>주민번호</label> <input type="text" name="idnum1" id="idnum1" maxlength="6"
									class="user_ResiNum"><span class="sc">-</span> <input type="text" name="idnum2" id="idnum2" maxlength="1"
									class="user_ResiNum">
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label>전화번호</label> 
								<input type="text" name="phonenum1" id="phonenum1"
									class="user_num" value="010" maxlength="3" ><span class="sc">-</span> <input
									type="text" name="phonenum2" id="phonenum2" maxlength="4" class="user_num"><span class="sc">-</span> <input
									type="text" name="phonenum3" id="phonenum3" maxlength="4" class="user_num">
									<input type="hidden" name="phonenum" id="phonenum">
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label>내선번호</label> 
								<input type="text" name="officenum1" id="officenum1"
									class="user_num" value="061" maxlength="3"><span class="sc">-</span> <input
									type="text" name="officenum2" id="officenum2" maxlength="3" class="user_num"><span class="sc">-</span> <input
									type="text" name="officenum3" id="officenum3" maxlength="4" class="user_num">
									<input type="hidden" name="officenum" id="officenum">
							</div>

							<div class="user_form_group user_documentfilters_line">
								<label>메일주소</label> 
								<input type="text" name="email1" id="email1" class="user_documentNum" />
                                <div class="user_email02">
                                  <span class="sc">@</span>
                                </div>  
                                	<input type="text" list="textData" name="email2" id="email2" class="document_select">
										<datalist id="textData">
											<option value="google.com">
											<option value="naver.com">
											<option value="kakaocorp.com">
										</datalist>
								<input type="hidden" name="email" id="email" value="email">
							</div>
							<div class="user_option_group">
								<div class="user_form_group user_documentfilters_line">
									<label class="user_label03">부서</label> <select class="document_select" name="team" id="team">
										<option value="">부서</option>
										<option value="임원">임원</option>
										<option value="개발">개발</option>
										<option value="디자인">디자인</option>
										<option value="경영지원">경영지원</option>
									</select>
								</div>
								<div class="user_form_group user_documentfilters_line">
									<label class="user_label06">직급</label> <select class="document_select"
										name="level" id="level">
										<option value="">직급</option>
										<option value="사장">사장</option>
										<option value="팀장">팀장</option>
										<option value="대리">대리</option>
										<option value="사원">사원</option>
									</select>
								</div>
								<div class="user_form_group user_documentfilters_line">
									<label class="user_label07">상태</label> <select class="document_select"
										name="user_status" id="user_status">
										<option value="">상태</option>
										<option value="재직">재직</option>
										<option value="퇴직">퇴직</option>
										<option value="휴직">휴직</option>
									</select>
								</div>
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label>사원번호</label> <input type="text" name="usernum" id="usernum" class="user_writeDate" value="${ usernum }"/> 
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label05">사원PW</label> <input type="password" name="userpw" id="userpw"
									class="user_documentNum" />
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label>입사일자</label> 
								<input type="date" class="user_writeDate" id="joindate" name="joindate"/>
							</div>
						</div>
						<div class="cteam_drafter_box">
							<div class="cteam_person_card">
								<label>※ 권한 여부 : &nbsp;</label>&nbsp;
								<div class="authority">
									<input type="radio" name="authority" id="authority1" value="0" >사원 &nbsp; 
									<input type="radio" name="authority" id="authority2" value="1">관리자
								</div>
							</div>
						</div>
					</div>
					<div class="cteam_btn_location">
						<button type="button" class="cteam_btn_style" id="writeCancel" >등록취소</button>
						<button type="button" class="cteam_btn_style" id="writeOK" onclick="DoInsert();">등록완료</button>
					</div>
				</form>
			</div>
		</div>
	</div>
</main>
<!-------------------------------------------------------- 사원세부정보 등록하기 종료 ----------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>