<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "user");
%>    
<%@ include file="../include/header.jsp" %>
<script>
function submitFilter() {
    const mode = getModeFromFilter();
    const form = document.getElementById('filterForm');
    
    
    form.querySelector('input[name="mode"]').value = mode;
    form.submit();  // 폼 전송
}


function getModeFromFilter() {
    const status = document.querySelector('select[name="status"]').value;
    let mode = '0';  // 전체 목록
    
    if (status === '대기중') {
        mode = '1';  // 결재대기
    } else if (status === '반려') {
        mode = '2';  // 결재반려
    } else if (status === '진행중') {
        mode = '3';  // 결재진행
    } else if (status === '승인') {
        mode = '4';  // 결재승인
    }
    return mode;
}
</script>
<!----------------------------------------------------결재 승인 목록 시작------------------------------------------------------------------>
<c:set var="title" value=""></c:set>
<c:choose>
	<c:when test="${param.mode == null || param.mode == '0'}">
		<c:set var="title" value="전체"></c:set>
	</c:when>
	<c:when test="${param.mode == '1'}">
		<c:set var="title" value="대기중"></c:set>
	</c:when>
	<c:when test="${param.mode == '2'}">
		<c:set var="title" value="반려"></c:set>
	</c:when>
	<c:when test="${param.mode == '3'}">
		<c:set var="title" value="진행중"></c:set>
	</c:when>
	<c:when test="${param.mode == '4'}">
		<c:set var="title" value="승인"></c:set>
	</c:when>			
</c:choose>
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="documentlist_title">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">내 결재 관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; <c:out value="${ title }"></c:out> </h4>
				</div>
				<hr>
				<form action="/ERP/approval/list.do" method="get" id="filterForm">
					<div class="cteam_filters">
						<select class="cteam_filter" name="kind" onchange="document.getElementById('filterForm').submit();">
							<option value="">문서구분</option>
							<option value="품의서" ${param.kind =='품의서' ? 'selected':''}>품의서</option>
							<option value="기안서" ${param.kind =='기안서' ? 'selected':''}>기안서</option>
							<option value="연차신청서" ${param.kind =='연차신청서' ? 'selected':''}>연차신청서</option>
						</select> 
						<select class="cteam_filter" name="mode" onchange="document.getElementById('filterForm').submit();">
					        <option value="0" ${param.mode == null || param.mode == '전체' ? 'selected' : ''}>전체</option>
					        <option value="1" ${param.mode == 1 ? 'selected' : ''}>대기중</option>
					        <option value="2" ${param.mode == 2 ? 'selected' : ''}>반려</option>
					        <option value="3" ${param.mode == 3 ? 'selected' : ''}>진행중</option>
					        <option value="4" ${param.mode == 4 ? 'selected' : ''}>승인</option>
					    </select>
						<div class="cteam_search">
							<input type="text" id="searchInput" name="keyword" placeholder="검색" value="${param.keyword}">
							<button type="submit" id="searchButton" class="cteam_searchButton">🔍</button>
						</div>
					</div>
				</form>
			</div>
			<c:forEach var="app" items="${approvalList}">
				<div class="approval_list">
					<div class="cteam_approval_item_inner">
						<a href="view.do?approval_no=${app.approval_no}&mode=${mode}" class="cteam_card_link">
							<div class="cteam_info_group">
								<div class="cteam_left">
									<div class="cteam_icon">📄</div>
									<div class="cteam_title">
										기본 양식<br>${app.kind}
									</div>
									<div class="info">
										<strong>${app.drafter.name}</strong><br> ${app.drafter.level} · ${app.drafter.team}
									</div>	
								</div>
								<div class="cteam_desc">${app.approval_title}</div>
							</div>
							<div class="cteam_right">
								<div class="cteam_date">${app.writedate}</div>
								<c:choose>
									<c:when test="${app.document_status == '승인'}">
										<div class="cteam_status_approval">승인</div>
									</c:when>
									<c:when test="${app.document_status == '진행중'}">
										<div class="cteam_status_ongoing">진행중</div>
									</c:when>
									<c:when test="${app.document_status == '반려'}">
										<div class="cteam_status_rejection">반려</div>
									</c:when>
									<c:when test="${app.document_status == '대기중'}">
										<div class="cteam_status_pending">대기중</div>
									</c:when>
								</c:choose>
							</div>
						</a>
					</div>
				</div>
			</c:forEach>
			<c:if test="${empty approvalList}">
				<div class="approval_list">
					<div class="cteam_approval_item_inner">
						<p>결재 문서가 없습니다.</p>
					</div>
				</div>
			</c:if>
			<div class="list-footer">
				<nav aria-label="..." class="pagination-nav">
					<div class="pagination-nav">
						<ul class="pagination">
							<c:forEach var="i" begin="${startbk}" end="${endbk}">
								<li class="page-item ${i == page ? 'active' : ''}">
									<a class="page-link" href="?page=${i}&kind=${kind}&status=${status}&keyword=${keyword}&mode=${mode}">${i}</a>
								</li>
							</c:forEach>
						</ul>
					</div>
				</nav>
			</div>
		</div>
	</div>
</main>
<!----------------------------------------------------결재 승인 목록 종료------------------------------------------------------------------>

<%@ include file="../include/footer.jsp" %>