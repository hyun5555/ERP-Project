<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "recv");
%>     
<%@ include file="../include/header.jsp" %>

<script>
function submitFilter() {
    const form = document.getElementById('filterForm');
    form.submit();  // 폼 전송
}
</script>
<!----------------------------------------------------결재 승인 목록 시작------------------------------------------------------------------>
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="documentlist_title">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">내 결재 관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; 결재수신</h4>
				</div>
				<hr>
				<form action="/ERP/approval/recv.do" method="get" id="filterForm">
					<div class="cteam_filters">
						<select class="cteam_filter" name="kind" onchange="document.getElementById('filterForm').submit();">
							<option value="">문서구분</option>
							<option value="품의서" ${param.kind =='품의서' ? 'selected':''}>품의서</option>
							<option value="기안서" ${param.kind =='기안서' ? 'selected':''}>기안서</option>
							<option value="연차신청서" ${param.kind =='연차신청서' ? 'selected':''}>연차신청서</option>
						</select>
						<select class="cteam_filter" name="status" onchange="document.getElementById('filterForm').submit();">
					        <option value="" ${empty param.status ? 'selected' : ''}>전체</option>
					        <option value="대기중" ${param.status == '대기중' ? 'selected' : ''}>대기중</option>
					        <option value="반려" ${param.status == '반려' ? 'selected' : ''}>반려</option>
					        <option value="진행중" ${param.status == '진행중' ? 'selected' : ''}>진행중</option>
					        <option value="승인" ${param.status == '승인' ? 'selected' : ''}>승인</option>
					    </select>
						<div class="cteam_search">
							<input type="text" id="searchInput" name="keyword" placeholder="검색" value="${param.keyword}">
							<button id="searchButton" class="cteam_searchButton">🔍</button>
						</div>
					</div>
				</form>
			</div>
			<!--결재 수신 내역 버튼-->
			<c:forEach var="app" items="${approvalList}">
				<div class="approval_list">
					<div class="cteam_approval_item_inner">
						<a href="view.do?approval_no=${app.approval_no}&menu=recv" class="cteam_card_link">
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
								<div class="cteam_desc" style="margin-left: 20px;">${app.approval_title}</div>
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
									<a class="page-link" href="?page=${i}&kind=${kind}&status=${status}&keyword=${keyword}">${i}</a>
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