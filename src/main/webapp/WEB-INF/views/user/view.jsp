<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "user-edit");
%>    
<%@ include file="../include/header.jsp" %>
<%@ page import="com.erp.vo.*" %>
<%


	//관리자인지 아닌지 판별
    userVO loginUser = (userVO) session.getAttribute("loginUser");
    boolean isAdmin = loginUser != null && loginUser.isAuthority();

    // JSTL에서도 쓸 수 있도록 EL attribute로 넘김
    pageContext.setAttribute("isAdmin", isAdmin);
    pageContext.setAttribute("loginUser", loginUser);
%>

<!-------------------------------------------------------- 사원세부 정보 뷰 시작 ----------------------------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="card-title mb-1 p-3">
					<div class="cteam_subtitle">
						<h2 class="cteam_head_maintitle">사원관리</h2>
						&nbsp;
						<h4 class="cteam_head_sub">&gt; 사원세부정보</h4>
					</div>
				</div>
				<hr>
				<div class="cteam_header_row">
					<h3>사원세부정보</h3>
				</div>
				<div class="cteam_middle_row">
					<div class="cteam_view_filters">
						<div class="user_form_group user_documentfilters_line">
							<label class="user_label01">사원명</label> <input type="text"
								name="member_name" class="user_documentNum" value="${user.name }"
								readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>주민번호</label> <input type="text" name="juminbunho1"
								class="user_ResiNum" value="${user.idnum1 }" readonly /><span class="sc">-</span>
								<input type="text" class="user_ResiNum"
								value="${user.idnum2 }" readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>전화번호</label> <input type="text" name="tel1"
								class="user_num" value="${user.phonenum }" readonly style="width: 100px"/>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>내선번호</label> <input type="text" name="tel1"
								class="user_num" value="${user.officenum }" readonly style="width: 100px"/>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>메일주소</label> <input type="text" name="email1"
								class="user_documentNum" value="${user.email }" readonly />
						</div>
						<div class="user_option_group">
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label03">부서</label> <input type="text"
									class="user_documentNum" value="${user.team }" readonly />
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label06">직급</label> <input type="text"
									class="user_documentNum" value="${user.level }" readonly />
							</div>
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label07">상태</label> <input type="text"
									class="user_documentNum" value="${user.user_status }" readonly />
							</div>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label class="user_label04">사원ID</label> <input type="text"
								name="member_id" class="user_documentNum" value="${user.usernum }"
								readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label class="user_label05">사원PW</label> <input type="password"
								name="member_pw" class="user_documentNum" value="${user.userpw }"
								readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>입사일자</label> <input type="text" class="user_documentNum"
								value="${user.joindate }" readonly />
						</div>
					</div>
				</div>
				<div class="cteam_btn_location">
					<button class="cteam_btn_style"
						onclick="location.href='/ERP/user/list.do'">목록</button>
					<c:if test="${isAdmin or loginUser.usernum eq user.usernum }">
						<button class="cteam_btn_style"
						onclick="location.href='/ERP/user/modify.do?usernum=${user.usernum}'">수정</button>
						
					</c:if>
				</div>
			</div>
		</div>
	</div>
</main>
			<!-------------------------------------------------------- 사원세부 정보 뷰 종료 ----------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>