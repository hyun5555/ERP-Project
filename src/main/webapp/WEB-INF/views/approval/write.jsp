<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "write");
%>    
<%@ include file="../include/header.jsp" %>

<!----------------------------------------------------기안서 작성 시작-------------------------------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">내 결재 관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; 결재 작성</h4>
				</div>
				<hr>
				<div class="cteam_header_row">
					<h3>기안서</h3>
				</div>
				<div class="cteam_header_row cteam_button_group"></div>
				<form action ="/ERP/approval/write.do" method="post" enctype="multipart/form-data" id="writeOK"name="writeOk">
					<div class="cteam_middle_row">
						<div class="cteam_view_filters">
							<div class="cteam_form_group cteam_documentfilters_line">
								<label for="approval-kind">문서구분</label> 
								<select class="document_select" id="doc_select" name="kind">
									<option>문서구분</option>
									<option>연차신청서</option>
									<option>품의서</option>
									<option>기안서</option>
								</select>
							</div>
							<div class="cteam_form_group cteam_documentfilters_line">
								<label>품의번호</label> 
									<input type="text" class="cteam_documentNum" 
									id="approval_code" name="approval_code"/>
							</div>
							<div class="cteam_form_group cteam_documentfilters_line">
								<label>작성일자</label> 
									<input type="text" class="cteam_writeDate" 
									id="writedate" name="writedate" value="${appVO.writedate}" readonly/>
							</div>
						</div>
						<div class="cteam_drafter_box">
							<label>기안자</label>
							<div class="cteam_person_card">
								<div class="cteam_circle_avatar">${loginUser.name}</div>
								<div class="info">
									<strong>${loginUser.name}</strong><br> ${loginUser.level} · ${loginUser.team}
								</div>
							</div>
						</div>
					</div>
					<div class="cteam_document_title_group">
						<label>제목</label> 
						<input type="text" class="cteam_document_title_input" placeholder="제목을 입력해주세요"
						id="approval-title" name="approval_title"/>
					</div>
					<div class="cteam_notearea_group">
						<label>내용</label>
						<div class="cteam_notearea">
							<textarea id="approval-note" name="approval_content" placeholder="내용을 입력해주세요"></textarea>
						</div>
					</div>
					<div class="cteam_form_group">
						<label>결재선</label>
						<div class="cteam_approval_line" style="display: flex; flex-wrap: wrap; align-items: center;">
							<div id="selectedUsers" class="selected-user-wrapper" style="display: flex; flex-wrap: wrap;">
								<!-- 결재선 사원이 여기 추가됨 -->
							</div>
							<button class="add_approver" data-bs-toggle="modal"
								data-bs-target="#exampleModal" id="approval-add"
								name="approval-add" type="button">+</button>
						</div>
					</div>
					<div class="modal fade" id="exampleModal" tabindex="-1"
						aria-labelledby="exampleModalLabel" aria-hidden="true">
						<div class="modal-dialog modal-dialog-scrollable">
							<div class="modal-content">
								<div class="modal-header">
									<h1 class="modal-title fs-5" id="exampleModalLabel">구성원</h1>
									<button type="button" class="btn-close"
										data-bs-dismiss="modal" aria-label="Close"></button>
								</div>
								<div class="modal-body">
									<div class="modal-body member_list_body">
										<c:set var="prevTeam" value="99999"></c:set>
										<div class="member_group">
											<c:forEach var="user" items="${modalList}">	
												<c:if test="${ user.team != prevTeam }">											
													<div class="group_title toggle-btn" onclick="$('#${ user.team }').toggle();">
														<c:if test="${ user.team == null ||  user.team  == '' }">
														임원
														</c:if>
														<c:if test="${ user.team != null &&  user.team != '' }">
														${ user.team } 
														</c:if>
														▿
														<c:set var="prevTeam" value="${ user.team }"></c:set>
													</div>
												</c:if>
												<div class="member clickable" data-usernum="${ user.usernum }"	data-username="${ user.name }" data-team="${ user.team }"
												id="${ user.team }">
													<div class="cteam_circle_avatar">${ user.name }</div>
													<div class="info">
														<strong>${ user.name }</strong><br>${ user.level } · ${ user.team }
													</div>
												</div>												
											</c:forEach>
										</div>
									</div>
								</div>
							</div>
						</div>
					</div>
					<div class="cteam_form_group">
						<label><span>📎</span> 첨부파일 등록</label>
						<div class="cteam_file_line" style="width:90%">
							<input type="file" id="attach" name="attach" onchange="setFileName(this);" style="display: none;"/> 
							<span id="fileName" style="width:90%">파일이 선택되지 않았습니다</span>
							<button type="button" id="file-add" name="file-add" onclick="addAttach(this);">추가</button>
							<button type="button" id="file-del" name="file-del" onclick="removeAttach(this);">삭제</button>
						</div>				
					</div>
					<div class="cteam_btn_location">
						<a href="list.do"><button class="cteam_btn_style" id="writeCancel" name="writeCancel">등록취소</button></a>
						<button class="cteam_btn_style" type="submit" id="writeComplate" name="writeComplate">등록완료</button>
					</div>
				</form>
			</div>
		</div>
	</div>
</main>
<!----------------------------------------------------기안서 작성 종료-------------------------------------------------------------------->
<script src="${pageContext.request.contextPath}/resources/js/common.js"></script>
<%@ include file="../include/footer.jsp" %>