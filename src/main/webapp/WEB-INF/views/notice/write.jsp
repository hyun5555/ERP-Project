<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%
	pageContext.setAttribute("menu", "notice");
%>
<%@ include file="../include/header.jsp"%>

<script>

window.onload = function(){
	$("#notice_title").focus();
	
	$("#writeCancel").click(function(){
		if(confirm("등록취소 하시겠습니까?") == true)
		{
			location.href = "list.do";
		}
	});
}

function DoInsert()
{
	if($("#notice_title").val() == ""){
		alert("제목을 입력하세요.");
		$("#notice_title").focus();
		return;
	}	
	/*
	if($("#notice_team").val() == ""){
		alert("공지대상을 선택하세요.");
		$("#notice_team").focus();
		return;
	}
	*/
	var count = 0;
	$("input:checkbox[name='notice_team']").each(function()
	{
		if(this.checked == true) count++;
	});
	if(count == 0){
		alert("공지대상을 선택하세요.");
		return;
	}
	
	if($("#notice_content").val() == ""){
		alert("내용을 입력하세요.");
		$("#notice_content").focus();
		return;
	}	
	
	if(confirm("게시물을 등록하시겠습니까?") == true)
	{
		$("#write").submit();
	}		
}


</script>

<!-------------------------------------  공지사항 작성 시작 ---------------------------------------------->
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="card-title mb-1 p-3">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">공지사항</h2>
				</div>
			</div>
			<hr>
			<form id="write" name="write" action="writeOK.do" method="post" enctype="multipart/form-data">
				<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
				<div class="notice-form">
					<div class="notice-row">
						<label class="notice-label required">제목</label> 
						<label> 
						<input type="checkbox" name="is_important" id="is_important" value="1"/> 중요
						</label> 
						<input type="text" class="notice-input long" name="notice_title" id="notice_title" />
					</div>
					<div class="notice-row">
						<label class="notice-label">메인노출</label> 
						<label> 
						<input type="checkbox" name="is_main" id="is_main" value="1"/> 표시
						</label>
					</div>
					<div class="notice-row">
						<label class="notice-label">공지대상</label> <label
							class="notice-checkbox-team" > 
							<input type="checkbox" name="notice_team" id="notice_team" value="100"/> 개발
						</label> 
						<label class="notice-checkbox-team" > 
							<input type="checkbox" name="notice_team" id="notice_team" value="200"/> 디자인
						</label> 
						<label class="notice-checkbox-team"  > 
							<input type="checkbox" name="notice_team" id="notice_team" value="300"/> 경영지원
						</label>
					</div>
					<!--  
					<div class="notice-row">
						<label class="notice-label">첨부파일</label>
						<div class="file-upload-box">
							<input type="file" name="fileInput" id="fileInput" multiple style="display: none;" />
							<div class="file-upload-area">
								<div class="file-icon">📥</div>
								<p>마우스로 파일을 끌어놓으세요</p>
							</div>
							<div class="file-upload-buttons">
								<button class="file-btn"
									onclick="document.getElementById().click()">파일 선택</button>
								<button class="file-btn" onclick="uploadFile()">업로드</button>
							</div>
						</div>
					</div>
					-->
					<div class="notice-row">
						<label class="notice-label">첨부파일</label>
						<input type="file" name="fileInput" id="fileInput"/>
					</div>					
					<div class="cteam_notearea">
						<textarea name="notice_content" id="notice_content" style="height:400px;" placeholder="내용을 입력해주세요"></textarea>
					</div>
					<div class="user-reg-del">
						<button type="button" class="cteam_btn_style"
							name="writeCancel" id="writeCancel">등록취소</button>
						<button type="button" class="cteam_btn_style" name="writeOK" id="writeOK"
							onclick="DoInsert();">등록완료</button>
					</div>
				</div>
			</form>
		</div>
	</div>
</main>
<!-------------------------------------  공지사항 작성 종료 ---------------------------------------------->


<%@ include file="../include/footer.jsp"%>
