<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "notice");
%>     
<%@ include file="../include/header.jsp" %>
<!-------------------------------------  공지사항 뷰 시작 ----------------------------------------------->
<script>
function DoDelete(no)
{
	if(confirm("해당 게시물을 삭제하시겠습니까?") != 1)
	{
		return;	
	}
	document.location = "/ERP/notice/delete.do?notice_no=" + no;
}
</script>
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="card-title mb-1 p-3">
					<div class="cteam_subtitle">
						<h2 class="cteam_head_maintitle">공지사항</h2>
					</div>
				</div>
				<hr>
				<div class="cteam_middle_row">
					<div class="cteam_view_filters">
						<div class="cteam_form_group cteam_document_type">
							<label>제목</label>
							<label>
							<c:if test="${ notice.is_important == true }" >
								<span class="badge rounded-pill text-bg-danger">중요</span>
							</c:if>
							${ notice.notice_title }
							</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>공지대상</label> <label>
							<c:forEach var="item" items="${team}">
								${ item.team_name }
							</c:forEach>
							</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>공지일자</label> <label>${ notice.noticedate }</label>
						</div>
					</div>
					<div class="cteam_drafter_box">
						<label>작성자</label>
						<div class="cteam_person_card">
							<div class="cteam_circle_avatar">${ notice.username }</div>
							<div class="info">
								<strong>${ notice.usernum }</strong><br>${ notice.username }
							</div>
						</div>
					</div>
				</div>				
				<div class="cteam_form_group">
					<label><span>📎</span> 첨부파일 </label>
					<div class="cteam_file_line">
						<c:if test="${ notice.fname != null and notice.fname != '' }">
							<span>${ notice.fname }</span>
							<button onclick="location.href='/ERP/notice/down.do?notice_no=${ notice.notice_no }'">다운로드</button>
						</c:if>
						<c:if test="${ notice.fname == null or notice.fname == '' }">
							<span>첨부된 파일이 없습니다.</span>
						</c:if>						
					</div>
				</div>				
				<div class="cteam_notearea_group">
					<label>내용</label>
					<div class="notearea">
						${ notice.notice_content }
					</div>
				</div>
				<div class="user-reg-del">
					<button class="cteam_btn_style" onclick="location.href='/ERP/notice/list.do'">목록</button>
					<c:if test="${ loginUser != null and loginUser.authority == true }">
						<button class="cteam_btn_style" id="notice-del" onclick="DoDelete(${ notice.notice_no });">삭제</button>
					</c:if>
				</div>
			</div>
		</div>
	</div>
</main>
<!-------------------------------------  공지사항 뷰 종료 ----------------------------------------------->


<%@ include file="../include/footer.jsp" %>