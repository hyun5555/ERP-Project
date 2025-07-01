<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<link rel="stylesheet" href="/ERP/resources/css/login.css">
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<meta charset="UTF-8">
<title>비밀번호 재설정</title>
<script>
window.onload = function(){
	$("#oldpw").focus();
	$("#btnChange").click(function(){
		DoSubmit();
	})
}

function DoSubmit(){
	if($("#oldpw").val() == ""){
		alert("기존 비밀번호를 입력하세요.")
		$("#oldpw").focus();
		return;
	}
	if($("#newpw").val() == ""){
		alert("새 비밀번호를 입력하세요.")
		$("#newpw").focus();
		return;
	}
	if($("#confirmpw").val() == ""){
		alert("확인 비밀번호를 입력하세요.")
		$("#confirmpw").focus();
		return;
	}
	if($("#confirmpw").val() != $("#newpw").val()){
		alert("새 비밀번호가 일치하지 않습니다. 비밀번호를 재입력하세요.")
		$("#newpw").focus();
		return;
	}
	
	if(confirm("비밀번호를 변경하시겠습니까?")==false){
		return;
	}
	
	$.ajax({
		type: "post",
		url : "/ERP/login/changepw.do",
		data :
		{
			oldpw : $("#oldpw").val(),
			newpw : $("#newpw").val()
		},
		dataType: "html",
		success : function(data){
			// 통신이 성공적으로 이루어졌을때 이 함수를 타게된다.
			data = data.trim();
			//alert(data);
			if(data == "OK")
			{
				document.location = "../main.do";
			}
			if(data == "ERROR")
			{
				alert("기존비밀번호가 일치하지 않습니다.");
				$("#oldpw").focus();
			}
		},
		
		//error : function(request, status, error) { 결과 에러 콜백함수 }
		error: function(xhr, status, error){
			// 통신 오류 발생시	
		},
		
		//complete : ajax 요청 완료시 실행할 이벤트 지정(function)
		complete : function(){
			// 통신이 성공하거나 실패했어도 마지막으로 이 함수를 타게된다.
		}			
	});	
	
	
}
</script>
</head>
<body>
	<div class="container">
		<div class="reset-box">
			<h2>비밀번호 재설정</h2>
			<form action="/ERP/login/changepw.do" method="post">
				<div class="form-group">
					<label for="oldpw">기존 비밀번호</label> <input type="password"
						id="oldpw" name="oldpw" />
				</div>
				<div class="form-group">
					<label for="newpw">새 비밀번호</label> <input type="password"
						id="newpw" name="newpw" />
				</div>
				<div class="form-group">
					<label for="confirmpw">새 비밀번호 확인</label> <input
						type="password" id="confirmpw" name="confirmpw" />
				</div>
				<button class="btn" id="btnChange" type="button">submit</button>
			</form>
		</div>
	</div>
</body>
</html>