<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "recv");
%>    
<%@ include file="../include/header.jsp" %>>
<!----------------------------------------------------기안서 보기 시작-------------------------------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="justify-content-start">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">내 결재 관리</h2>&nbsp;
					<h4 class="cteam_head_sub">&gt; 결재수신</h4>
					<h4 class="cteam_head_sub">&gt; 수신 보기</h4>
				</div>
				<hr>
				<div class="cteam_header_row">
					<h3>기안서</h3>
				</div>
				<div class="cteam_header_row cteam_button_group"></div>
				<div class="cteam_middle_row">
					<div class="cteam_view_filters">
						<div class="cteam_form_group cteam_document_type">
							<label>문서구분</label> <label>연차신청서</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>품의번호</label> <label>20250403-1111111</label>
						</div>
						<div class="cteam_form_group cteam_document_type">
							<label>작성일자</label> <label>2025 / 04 / 03</label>
						</div>
					</div>
					<div class="cteam_drafter_box">
						<label>기안자</label>
						<div class="cteam_person_card">
							<div class="cteam_circle_avatar">가나</div>
							<div class="info">
								<strong>김가나</strong><br> 대리 · 경영지원팀
							</div>
						</div>
					</div>
				</div>
				<div class="cteam_document_title_group">
					<label>제목</label>
					<div class="cteam_document_title">제목입니다</div>
				</div>
				<div class="cteam_notearea_group">
					<label>내용</label>
					<div class="notearea">내용입니다</div>
				</div>
				<div class="cteam_form_group">
					<label>결재선</label>
					<!-- 수신자 입장에서의 결재선 승인/반려 버튼-->
					<div class="cteam_approval_line">
						<div class="cteam_person_card_approvalview">
							<div class="approval_line_title_group">
								<div class="person_info_box">
									<div class="cteam_circle_avatar">길동</div>
									<div class="info">
										<strong>홍길동</strong><br> 팀장 · 개발팀
									</div>
								</div>
								<input class="approval_review" type="text"
									placeholder="리뷰를 입력해주세요">
							</div>
							<div class="cteam_right">
								<button type="button" class="cteam_btn_style" id="approvalBtn">승인</button>
								<button type="button" class="cteam_btn_style" id="rejectionBtn">반려</button>
							</div>
						</div>
					</div>
					<!-- 수신자 입장에서의 결재선 승인/반려 버튼-->
					<div class="cteam_approval_line">
						<div class="cteam_person_card_approvalview">
							<div class="approval_line_title_group">
								<div class="person_info_box">
									<div class="cteam_circle_avatar">길동</div>
									<div class="info">
										<strong>홍길동</strong><br> 팀장 · 개발팀
									</div>
								</div>
								<input class="approval_review" type="text"
									placeholder="리뷰를 입력해주세요">
							</div>
							<div class="cteam_right">
								<button type="button" class="cteam_btn_style" id="approvalBtn">승인</button>
								<button type="button" class="cteam_btn_style" id="rejectionBtn">반려</button>
							</div>
						</div>
					</div>
					<div class="cteam_form_group">
					<label><span>📎</span> 첨부파일 </label>
					<div class="cteam_file_line">
						<span>연차신청서.hwp</span>
						<button>다운로드</button>
					</div>
				</div>
			</div>
		</div>
	</div>
</main>
<!----------------------------------------------------기안서 보기 종료-------------------------------------------------------------------->

<%@ include file="../include/footer.jsp" %>