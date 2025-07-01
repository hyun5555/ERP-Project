<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
if(request.getParameter("menu") != null)
{
	pageContext.setAttribute("menu", request.getParameter("menu"));	
}

%>    

<%@ include file="../include/header.jsp" %>

<!----------------------------------------------------기안서 보기 시작-------------------------------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">내 결재 관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; 결재 대기</h4>
				</div>
				<hr>
				<div class="cteam_header_row">
					<h3>기안서</h3>
				</div>
				<div class="cteam_header_row cteam_button_group"></div>
				<div class="cteam_middle_row">
					<div class="cteam_view_filters">
						<div class="cteam_form_group cteam_document_type">
							<label>문서구분</label> <label>${item.kind }</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>품의번호</label> <label>${item.approval_code }</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>작성일자</label> <label>${item.writedate }</label>
						</div>
					</div>
					<div class="cteam_drafter_box">
						<label>기안자</label>
						<div class="cteam_person_card">
							<div class="cteam_circle_avatar">${item.drafter.name}</div>
							<div class="info">
								<strong>${item.drafter.name}</strong><br> ${item.drafter.level} · ${item.drafter.team}
							</div>
						</div>
					</div>
				</div>
				<div class="cteam_document_title_group">
					<label>제목</label>
					<div class="cteam_document_title">${item.approval_title }</div>
				</div>
				<div class="cteam_notearea_group">
					<label>내용</label>
					<div class="notearea">${item.approval_content }</div>
				</div>
				<div class="cteam_form_group">
					<label>결재선</label>
					<!-- 기안자 입장에서의 결재선 리뷰 -->
					<c:forEach var="line" items="${addedLine}">
						<div class="cteam_approval_line">
							<div class="cteam_person_card_approvalview">
								<div class="approval_line_title_group">
									<div class="person_info_box">
										<div class="cteam_circle_avatar">${line.approver.name}</div>
										<div class="info">
											<strong>${line.approver.name}</strong><br>
											${line.approver.level} · ${line.approver.team}
										</div>
									</div>
									<div class="approval_review_view">${line.comment}</div>
								</div>
								<div class="cteam_right">
									<div class="cteam_date">
										<c:out value="${line.approval_date}" />
									</div>		
									<c:choose>
										<c:when test="${line.approval_status == '승인'}">
											<c:set var="statusClass" value="cteam_status_approval" />
										</c:when>
										<c:when test="${line.approval_status == '반려'}">
											<c:set var="statusClass" value="cteam_status_rejection" />
										</c:when>
										<c:when test="${line.approval_status == '진행중'}">
											<c:set var="statusClass" value="cteam_status_ongoing" />
										</c:when>
										<c:otherwise>
											<c:set var="statusClass" value="cteam_status_pending" />
										</c:otherwise>
									</c:choose>
									
									<div class="${statusClass}">
										<c:choose>
											<c:when test="${line.approval_status == '승인'}">승인</c:when>
											<c:when test="${line.approval_status == '반려'}">반려</c:when>
											<c:when test="${line.approval_status == '진행중'}">진행중</c:when>
											<c:otherwise>
												<strong>대기중</strong>
											</c:otherwise>
										</c:choose>
									</div>
								</div>
							</div>
						</div>
					</c:forEach>
				</div>
				<div class="cteam_form_group">
					<label><span>📎</span> 첨부파일 </label>
					<div class="cteam_file_line" style="width:90%;  min-height: 30px;" >
						<c:choose>
							<c:when test="${not empty appfileList}">
								<c:forEach var="appfile" items="${appfileList}">
									<a href="/ERP/approval/down.do?no=${appfile.approval_no}">${appfile.afname} </a>
								</c:forEach>
							</c:when>
							<c:otherwise>
						    	 <span id="fileName" style="width:90%"> 첨부파일이 없습니다.</span>
						    </c:otherwise>
						</c:choose>
					</div>
				</div>
				<br>
				<c:set var="isApprover" value="false" />
				<c:forEach var="line" items="${addedLine}">
					<c:if test="${line.approver.usernum eq sessionScope.loginUser.usernum}">
						<c:set var="isApprover" value="true" />
					</c:if>
				</c:forEach>
				<c:if test="${isApprover}">
					<form action="updateComment.do" method="post" id="updateComment">
						<input type="hidden" name="approval_no" value="${item.approval_no}" />
						<input type="hidden" name="usernum" value="${sessionScope.loginUser.usernum}" />
						<input type="hidden" name="approval_status" id="approval_status" />
						<div class="cteam_notearea_group">
							<label>의견</label>
							<textarea class="cteam_person_card_approvalview" 
							name="comment" id="comment"
							style="display: flex; justify-content: space-between; align-items: flex-start;"
							placeholder="의견을 입력하세요" ></textarea>
							<div class="cteam_right" style="justify-content: flex-end;">
								<button type="button" class="cteam_btn_style" onclick="submitApproval('승인')">승인</button>
								<button type="button" class="cteam_btn_style" onclick="submitApproval('반려')">반려</button>
							</div>
						</div>
					</form>
				</c:if>
				<div class="cteam_btn_location">
				    <c:if test="${sessionScope.loginUser.usernum eq item.usernum}">
				    	<input type="hidden" id="approvalStatus" value="${item.document_status}" />
				    	<button class="cteam_btn_style" id="modifybtn">수정하기</button>
				    	<button class="cteam_btn_style" id="deleteApp" onclick="deleteApproval(${item.approval_no})">삭제하기</button>
				    </c:if>
				</div>
			</div>
		</div>
	</div>
</main>
<!----------------------------------------------------기안서 보기 종료-------------------------------------------------------------------->
<script>
$(document).ready(function() 
{
    var approvalStatus = document.getElementById('approvalStatus').value;

    if (approvalStatus === "대기중" || approvalStatus === "반려") 
    {
        document.getElementById('modifybtn').style.display = "inline-block";
    }else
    {
    	document.getElementById('modifybtn').style.display = "none";	
    }

    $('#modifybtn').on('click', function() 
    {
        if (confirm('수정하시겠습니까?')) 
        {
            window.location.href = '/ERP/approval/modify.do?approval_no=${item.approval_no}&mode=${mode}';
        }
    });

    window.deleteApproval = function(no) 
    {
        if (confirm('문서를 삭제하시겠습니까?')) 
        {
            location.href = '/ERP/approval/delete.do?approval_no=' + no;
        }
    };
});
    
function submitApproval(status) {
    $('#approval_status').val(status);
    $('#updateComment').submit();
  }
</script>
<%@ include file="../include/footer.jsp" %>