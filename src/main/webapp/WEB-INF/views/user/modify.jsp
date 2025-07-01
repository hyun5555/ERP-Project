<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "user-edit");
%>    
<%@ include file="../include/header.jsp" %>
<script>
	window.onload = function(){
		$("#name").focus();
		
		$("#modifyCancel").click(function(){
			if(confirm("수정을 취소하겠습니까?")==true){
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

	function DoModify(){
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
		
		
		
		document.getElementById("modifyForm").submit();
		
	}
</script>

<!-------------------------------------------------------- 사원세부정보 수정하기 시작 ----------------------------------------------------------------->
			<main class="page-content">
				<div class="row mb-4">
					<div class="col-md-12">
						<div class="justify-content-start">
						<div class="card-title mb-1 p-3">
							<div class="cteam_subtitle">
								<h2 class="cteam_head_maintitle">사원관리</h2>&nbsp;
								<h4 class="cteam_head_sub">&gt; 사원세부정보(수정)</h4>
							</div>
						</div>
							<hr>
						<div class="cteam_header_row">
							<h3>사원세부정보</h3>
						</div>
							<div class="cteam_header_row cteam_button_group"></div>
							<form id="modifyForm" action="/ERP/user/modify.do" method="post">
								<div class="cteam_middle_row">
									<div class="cteam_view_filters">
										<div class="user_form_group user_documentfilters_line">
											<label class="user_label01">사원명</label> <input type="text" name="name" id="name"
												class="user_documentNum" value="${user.name }" />
										</div>
										<div class="user_form_group user_documentfilters_line">
											<label>주민번호</label> <input type="text" name="idnum1" type="text" id="idnum1"
												class="user_ResiNum"  value=${user.idnum1 }><span class="sc">-</span> <input type="text" id="idnum2"
												class="user_ResiNum" name="idnum2" value=${user.idnum2 }>
										</div>
										<div class="user_form_group user_documentfilters_line">
											<label>전화번호</label> <input type="text" name="phonenum1" type="text" id="phonenum1"
												class="user_num" value="${phonenum1 }"><span class="sc">-</span> <input
												type="text" name="phonenum2" id="phonenum2" class="user_num" value="${phonenum2 }"><span class="sc">-</span> <input
												type="text" name="phonenum3" class="user_num" id="phonenum3" value="${phonenum3 }">
										</div>
										<div class="user_form_group user_documentfilters_line">
											<label>내선번호</label> <input type="text" name="officenum1" type="text" id="officenum1"
												class="user_num" value="${officenum1 }"><span class="sc">-</span> <input
												type="text" name="officenum2" id="officenum2" class="user_num" value="${officenum2 }"><span class="sc">-</span> <input
												type="text" name="officenum3" id="officenum3" class="user_num" value=${officenum3 }>
										</div>
		
										<div class="user_form_group user_documentfilters_line">
											<label>메일주소</label>
											<input type="text" name="email1" id="email1" class="user_documentNum" value="${email1 }" />
			                                <div class="user_email02">
			                                  <span class="sc">@</span>
			                                </div>  
												<select class="document_select" name="email2" id="email2">
													<option value="google.com" ${email2 == 'google.com' ? 'selected' : ''}>google.com</option>
													<option value="naver.com" ${email2 == 'naver.com' ? 'selected' : ''}>naver.com</option>
													<option value="kakaocorp.com" ${email2 == 'kakaocorp.com' ? 'selected' : ''}>kakaocorp.com</option>
												</select>
										</div>
										<div class="user_option_group">
											<div class="user_form_group user_documentfilters_line">
												<label class="user_label03">부서</label> 
												<select class="document_select" name="team" id="team">
													<option value="">부서</option>
													<option value="임원" ${user.team == '임원' ? 'selected' : ''}>임원</option>
												    <option value="경영지원" ${user.team == '경영지원' ? 'selected' : ''}>경영지원</option>
												    <option value="개발" ${user.team == '개발' ? 'selected' : ''}>개발</option>
												    <option value="디자인" ${user.team == '디자인' ? 'selected' : ''}>디자인</option>
												</select>
											</div>
											<div class="user_form_group user_documentfilters_line">
												<label class="user_label06">직급</label> 
												<select class="document_select" name="level" id="name">
													<option value="">직급</option>
												    <option value="사장" ${user.level == '사장' ? 'selected' : ''}>사장</option>
												    <option value="팀장" ${user.level == '팀장' ? 'selected' : ''}>팀장</option>
												    <option value="대리" ${user.level == '대리' ? 'selected' : ''}>대리</option>
												    <option value="사원" ${user.level == '사원' ? 'selected' : ''}>사원</option>
												</select>
											</div>
											<div class="user_form_group user_documentfilters_line">
												<label class="user_label07">상태</label> 
												<select class="document_select" name="user_status" id="user_status">
													<option value="">상태</option>
												    <option value="재직" ${user.user_status == '재직' ? 'selected' : ''}>재직</option>
												    <option value="퇴직" ${user.user_status == '퇴직' ? 'selected' : ''}>퇴직</option>
												    <option value="휴직" ${user.user_status == '휴직' ? 'selected' : ''}>휴직</option>
												</select>     
											</div>
										</div>
										<div class="user_form_group user_documentfilters_line">
											<label class="user_label04">사원ID</label> 
											<input type="text" name="usernum_display" class="user_documentNum" id="usernum" value="${user.usernum }" readonly/>
    										<input type="hidden" name="usernum" value="${user.usernum }"/>
										</div>
										<div class="user_form_group user_documentfilters_line">
											<label class="user_label05">사원PW</label> <input type="password" name="userpw" id="userpw"
												class="user_documentNum" value=""/>
										</div>                     
										<div class="user_form_group user_documentfilters_line">
											<label>입사일자</label> <input type="text" name="joindate" id="joindate" class="user_writeDate" value="${user.joindate }" />
										</div>
									</div>
									<div class="cteam_drafter_box">
										<div class="cteam_person_card">
											<label>※ 권한 여부 : &nbsp;</label>&nbsp;
											<div class="authority">
												<c:if test="${user.authority == false }">
													<input type="radio" name="authority" value="0" checked>사원 &nbsp; 
													<input type="radio" name="authority" value="1">관리자
												</c:if>
												<c:if test="${user.authority == true }">
													<input type="radio" name="authority" value="0">사원 &nbsp; 
													<input type="radio" name="authority" value="1" checked>관리자
												</c:if>												
											</div>
										</div>
									</div>
								</div>
								<div class="cteam_btn_location">
									<button type="button" class="cteam_btn_style" id="modifyCancel">수정취소</button>
									<button type="button" class="cteam_btn_style" id="modifyOK" onclick="DoModify()">수정완료</button>
								</div>
							</form>
						</div>
					</div>
				</div>
			</main>
			<!-------------------------------------------------------- 사원세부정보 수정하기 종료 ----------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>