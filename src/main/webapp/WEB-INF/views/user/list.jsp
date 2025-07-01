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
<script>
	document.addEventListener("DOMContentLoaded", function () {
		const rows = document.querySelectorAll("tr.user-row");
		rows.forEach(row => {
			row.addEventListener("click", function () {
				const usernum = this.cells[0].innerText.trim(); // 1번째 열에서 사원번호 추출
				location.href = `/ERP/user/view.do?usernum=${usernum}`;
			});
		});
	});
	
	function confirmDelete(usernum) {
	    if (confirm("해당 사원을 삭제하시겠습니까?")) {
	        location.href = '/ERP/user/delete.do?usernum=' + usernum;
	    }
	}
	
	function toggleAll(source) {
		const checkboxes = document.querySelectorAll('.emp-del');
		checkboxes.forEach(checkbox => checkbox.checked = source.checked);
	}

	function confirmCheckDelete() {
		const checked = document.querySelectorAll('.emp-del:checked');
		if (checked.length === 0) {
			alert("삭제할 사원을 선택해주세요.");
			return;
		}
		if (confirm("선택한 사원들을 삭제하시겠습니까?")) {
			document.getElementById("checkDeleteForm").submit();
		}
	}
</script>

<!-------------------------------------------------------- 전체사원 목록 시작----------------------------------------------------------------->
			<hr>
			<main class="page-content">
				<div class="row mb-4">
					<div class="col-md-12">
						<div class="card-title mb-1 p-3">
							<div class="cteam_subtitle">
								<h2 class="cteam_head_maintitle">사원 관리</h2>
								<h4 class="cteam_head_sub">&gt; 전체사원목록</h4>
							</div>
						</div>
						<hr>
						<form action="/ERP/user/list.do" method="get" id="filterForm">
							<div class="cteam_filters">
							<select name="team" class="cteam_filter" onchange="document.getElementById('filterForm').submit();">
								<option value="">부서</option>
								<option value="개발" ${param.team eq '개발' ? 'selected' : ''}>개발</option>
								<option value="디자인" ${param.team eq '디자인' ? 'selected' : ''}>디자인</option>
								<option value="경영지원" ${param.team eq '경영지원' ? 'selected' : ''}>경영지원</option>
							</select>
					
							<select name="level" class="cteam_filter" onchange="document.getElementById('filterForm').submit();">
								<option value="">직급</option>
								<option value="사원" ${param.level eq '사원' ? 'selected' : ''}>사원</option>
								<option value="대리" ${param.level eq '대리' ? 'selected' : ''}>대리</option>
								<option value="팀장" ${param.level eq '팀장' ? 'selected' : ''}>팀장</option>
								<option value="사장" ${param.level eq '사장' ? 'selected' : ''}>사장</option>
							</select>
					
							<select name="user_status" class="cteam_filter" onchange="document.getElementById('filterForm').submit();">
								<option value="">상태</option>
								<option value="재직" ${param.user_status eq '재직' ? 'selected' : ''}>재직</option>
								<option value="휴직" ${param.user_status eq '휴직' ? 'selected' : ''}>휴직</option>
								<option value="퇴직" ${param.user_status eq '퇴직' ? 'selected' : ''}>퇴직</option>
							</select>
								<div class="cteam_search">
									<select name="searchKey" class="cteam_filter" style="margin-right: 10px">
										<option value="usernum" ${param.searchKey eq 'usernum' ? 'selected' : ''}>사원번호</option>
										<option value="name" ${param.searchKey eq 'name' ? 'selected' : ''}>이름</option>
									</select>
								
									<input type="text" id="searchInput" placeholder="검색" name="searchWord" value="${param.searchWord}">
									<button type="submit" id="searchButton" class="cteam_searchButton">🔍</button>
								</div>

							</div>
						</form>
						<form id="checkDeleteForm" action="/ERP/user/deleteSelected.do" method="post">
							<div class="staff-management">
								<div class="table-responsive-md">
									<table class="table table-hover">
										<thead class="mana-category">
											<tr>
												<th scope="col"><input type="checkbox" id="selectAll" />
												</th>
												<th scope="col">사원번호</th>
												<th scope="col">부서</th>
												<th scope="col">직급</th>
												<th scope="col">사원명</th>
												<th scope="col">내선번호</th>
												<th scope="col">메일주소</th>
												<th scope="col">상태</th>
												<th scope="col">수정/삭제</th>
											</tr>
										</thead>
										<tbody class="mana-list">
											<c:forEach var="user" items="${userList}">
												<tr style="cursor:pointer;" onclick="location.href='/ERP/user/view.do?usernum=${user.usernum}'">
												<th scope="col">
												<input type="checkbox" class="emp-del" value="${user.usernum}" name="usernums" onclick="event.stopPropagation();"/>
												</th>
												<th scope="row" class="mana-staff-num click-cell">${user.usernum }</th>
												<td class="mana-team click-cell">${user.team }</td>
												<td class="mana-level click-cell">${user.level }</td>
												<td class="mana-staff-name click-cell">${user.name }</td>
												<td class="mana-phnum click-cell">${user.officenum }</td>
												<td class="mana-email click-cell">${user.email }</td>
												<td class="mana-status click-cell">${user.user_status }</td>
												<td class="mama-btn">
												
												<c:if test="${isAdmin or loginUser.usernum eq user.usernum}">
													<a class="btn btn-sm btn-outline-lightning rounded-0 mr-2"
													   href="/ERP/user/modify.do?usernum=${user.usernum}"
													   onclick="event.stopPropagation();">
													   <i class="far fa-edit"></i>
													</a>
												</c:if>
	
			
												<%if(isAdmin){ %>
													<a class="btn btn-sm btn-outline-lightning rounded-0"
													   href="javascript:void(0);"
													   onclick="event.stopPropagation(); confirmDelete('${user.usernum}')">
													   <i class="far fa-trash-alt"></i>
													</a>
												<%} %>
												</td>
								
												</tr>
											</c:forEach>
											
										</tbody>
									</table>
								</div>
							</div>
						</form>
						<div class="list-footer">
							<div class="pagination-nav">
								<ul class="pagination">
									<c:forEach var="i" begin="1" end="${totalPages}">
										<li class="page-item ${i == pageno ? 'active' : ''}">
											<a class="page-link" href="?pageno=${i}
											<c:if test='${param.team != null}'> &team=${param.team}</c:if>
											<c:if test='${param.level != null}'> &level=${param.level}</c:if>
											<c:if test='${param.user_status != null}'> &user_status=${param.user_status}</c:if>
											<c:if test='${param.searchKey != null}'> &searchKey=${param.searchKey}</c:if>
											<c:if test='${param.searchWord != null}'> &searchWord=${param.searchWord}</c:if>">
												${i}
											</a>
										</li>
									</c:forEach>
								</ul>
							</div>

							<% if(isAdmin){ %>
							<div class="user-reg-del">
								<button class="cteam_btn_style" type="button" onclick="location.href='/ERP/user/write.do'">사원등록</button>
								<button class="cteam_btn_style" onclick="confirmCheckDelete()">일괄삭제</button>
							</div>
							<% } %>


						</div>
					</div>
				</div>
			</main>
			<!-------------------------------------------------------- 전체사원 목록 종료 ----------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>

