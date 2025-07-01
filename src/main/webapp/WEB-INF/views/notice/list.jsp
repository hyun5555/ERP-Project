<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "notice");
%>     
<%@ include file="../include/header.jsp" %>
<!----------------------------------------------------공지사항 목록 시작---------------------------------------------------------------------->
<script>
function DoSearch()
{
	document.location = "list.do?notice_team=${ notice_team }&searchWord=" + $("#searchWord").val();
}
</script>
<hr>
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="card border-0 rounded-0">
				<div class="card-title mb-1 p-3">
					<h2 class="cteam_head_maintitle">공지 사항</h2>
				</div>
				<hr>
				<div class="nav-container">
					<ul class="nav nav-underline">
						 <li class="nav-item">
						 	<c:if test="${ notice_team == null }">
						 	<a class="nav-link active" aria-current="page" href="list.do">전체</a>
						 	</c:if>
						 	<c:if test="${ notice_team != null }">
						 	<a class="nav-link" href="list.do">전체</a>
						 	</c:if>						 	
						 </li>
						 <li class="nav-item">
						 	<c:if test="${ notice_team == '100' }">
						 		<a class="nav-link active" aria-current="page" href="list.do?notice_team=100">개발</a>
						 	</c:if>
						 	<c:if test="${ notice_team != '100' }">
						 		<a class="nav-link" href="list.do?notice_team=100">개발</a>
						 	</c:if>						 	
						 </li>
						 <li class="nav-item">
						 	<c:if test="${ notice_team == '200' }">
						 		<a class="nav-link active" aria-current="page" href="list.do?notice_team=200">디자인</a>
						 	</c:if>
						 	<c:if test="${ notice_team != '200' }">
						 		<a class="nav-link" href="list.do?notice_team=200">디자인</a>
						 	</c:if>
						 </li>
						 <li class="nav-item">
						 	<c:if test="${ notice_team == '300' }">
						 		<a class="nav-link active" aria-current="page" href="list.do?notice_team=300">경영지원</a>
						 	</c:if>
						 	<c:if test="${ notice_team != '300' }">
						 		<a class="nav-link" href="list.do?notice_team=300">경영지원</a>
						 	</c:if>		
						 </li>
					</ul>
					<div class="cteam_search">
						<input type="text" id="searchWord" name="searchWord" placeholder="검색">
						<button id="searchButton" class="cteam_searchButton" onclick="DoSearch();">🔍</button>
					</div>
				</div>
				<div class="notice-margin">
					<div class="table-responsive-md">
						<div class="notice-category">
							<div class="notice-category-header">
								<div class="notice-category-num">번호</div>
								<div class="notice-category-title">제목</div>
								<div class="notice-category-team">부서</div>
								<div class="notice-category-author">작성자</div>
								<div class="notice-category-date">날짜</div>
							</div>
							<c:if test="${ total == 0 }">
								<div style="width:100%; height:150px;text-align:center;border:1px solid lightgray;padding : 100px 0;">조회된 자료가 없습니다.</div>
							</c:if>
							<c:forEach var="item" items="${list}">
								<a href="/ERP/notice/view.do?notice_no=${ item.notice_no }" class="notice-category-row">
									<div class="notice-category-num">${ item.notice_no }</div>
									<div class="notice-category-title">
										<c:if test="${ item.is_important == true }" >
											<span class="badge rounded-pill text-bg-danger">중요</span>&nbsp;
										</c:if>
										${ item.notice_title }
									</div>
									<div class="notice-category-team">
									<c:if test="${ notice_team == null }">전체</c:if>
									<c:if test="${ notice_team == '100' }">개발</c:if>
									<c:if test="${ notice_team == '200' }">디자인</c:if>
									<c:if test="${ notice_team == '300' }">경영지원</c:if>
									</div>
									<div class="notice-category-author">${ item.username }</div>
									<div class="notice-category-date">${ item.noticedate }</div>
								</a> 
							</c:forEach>
						</div>
					</div>
				</div>
			</div>
			<div class="list-footer">
				<nav aria-label="..." class="pagination-nav">
					<ul class="pagination">
						<li class="page-item">
						<c:if test="${ startbk > 10 }"> 
						<a class="page-link" href="list.do?page=${startbk - 1}&notice_team=${ notice_team }&searchWord=${ searchWord }" aria-label="Previous"> <span aria-hidden="true">&laquo;</span></a>
						</c:if> 
						</li>
						<c:forEach var="page" begin="${startbk}" end="${endbk}">
							<c:if test="${ currpage == page }"> 
								<li class="page-item active" aria-current="page">
								<a href="list.do?page=${page}&notice_team=${ notice_team }&searchWord=${ searchWord }"><span class="page-link">${page}</span></a>
								</li>
							</c:if>
							<c:if test="${ currpage != page }">
								<li class="page-item"><a class="page-link" href="list.do?page=${page}&notice_team=${ notice_team }&searchWord=${ searchWord }">${page}</a></li>
							</c:if>
						</c:forEach>
						<li class="page-item">
						<c:if test="${ endbk < maxpage }"> 
						<a class="page-link" href="list.do?page=${endbk + 1}&notice_team=${ notice_team }&searchWord=${ searchWord }" aria-label="Next"> <span aria-hidden="true">&raquo;</span></a>
						</c:if>
						</li>
					</ul>
				</nav>
				<div class="notice-reg">
					<button class="cteam_btn_style" type="button"
						onclick="location.href='/ERP/notice/write.do'">공지등록</button>
				</div>
			</div>
		</div>
	</div>
</main>

<!-------------------------------------------------------- 공지사항 목록 종료 ----------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>
