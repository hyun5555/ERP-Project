<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "user");
%>   
<link href="/ERP/resources/css/common.css" rel="stylesheet" type="text/css">
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" crossorigin="anonymous"/>
<!-------------------------------------------------------- 내 정보 뷰 시작 ----------------------------------------------------------------->
<main class="page-content  ps-4">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="cteam_header_row">
					<h3>내 정보</h3>
				</div>
				<div class="cteam_middle_row">
					<div class="cteam_view_filters">
						<div class="user_form_group user_documentfilters_line">
							<label class="user_label01">사원명</label> <input type="text"
								name="member_name" class="user_documentNum" value="${loginUser.name }"
								readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>주민번호</label> <input type="text" name="juminbunho1"
								class="user_ResiNum" value="${loginUser.idnum1 }" readonly /><span class="sc">-</span>
								<input type="text" class="user_ResiNum"
								value="${loginUser.idnum2 }" readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>전화번호</label> <input type="text" name="tel1"
								class="user_num" value="${loginUser.phonenum }" readonly style="width: 100px"/>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>내선번호</label> <input type="text" name="tel1"
								class="user_num" value="${loginUser.officenum }" readonly style="width: 100px"/>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>메일주소</label> <input type="text" name="email1"
								class="user_documentNum" value="${loginUser.email }" readonly />
						</div>
						<div class="user_option_group">
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label03">부서</label> <input type="text"
									class="user_documentNum" value="${loginUser.team }" readonly />
							</div>
						</div>
						<div class="user_option_group">							
							<div class="user_form_group user_documentfilters_line">
								<label class="user_label03">직급</label> <input type="text"
									class="user_documentNum" value="${loginUser.level }" readonly />
							</div>
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label class="user_label04">사원ID</label> <input type="text"
								name="member_id" class="user_documentNum" value="${loginUser.usernum }"
								readonly />
						</div>
						<div class="user_form_group user_documentfilters_line">
							<label>입사일자</label> <input type="text" class="user_joinDate"
								value="${loginUser.joindate }" readonly />
						</div>
					</div>
				</div>
				<div class="cteam_btn_location">
					<button class="cteam_btn_style" onclick="window.close()">닫기</button>
				</div>
			</div>
		</div>
	</div>
</main>

			<!-------------------------------------------------------- 내 정보 뷰 종료 ----------------------------------------------------------------->