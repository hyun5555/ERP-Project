<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<link rel="stylesheet" href="/ERP/resources/css/login.css">
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<meta charset="UTF-8">
<title>로그인</title>
</head>
<body>
<script>
window.onload = function(){
	$("#usernum").focus();
	
	$("#btnLogin").click(function(){
		DoSubmit();
	});
	
}

function SetUser(num,pw)
{
	$("#usernum").val(num);
	$("#userpw").val(pw);	
}

function DoSubmit(){
	if($("#usernum").val() ==""){
		alert("사원번호를 입력하세요.");
		$("#usernum").focus();
		return;
	}

	if($("#userpw").val() ==""){
		alert("비밀번호를 입력하세요.");
		$("#userpw").focus();
		return;
	}
	
	$.ajax({
		type: "post",
		url : "/ERP/login/login.do",
		data :
		{
			usernum : $("#usernum").val(),
			userpw : $("#userpw").val()
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
			if(data == "CHANGE")
			{
				document.location = "changepw.do";
			}
			if(data == "ERROR")
			{
				alert("사원번호 또는 비밀번호가 일치하지 않습니다.");
				$("#usernum").focus();
			}
		},
		error: function(xhr, status, error){
			// 통신 오류 발생시	
		},
		complete : function(){
			// 통신이 성공하거나 실패했어도 마지막으로 이 함수를 타게된다.
		}			
	});	
}
</script>
	<div class="container">
		<div class="login-box">
			<form action="/ERP/login/login.do" method="post" name="login">
				<div class="form-group">
					<label for="member-id">사원번호</label> <input type="text"
						id="usernum" name="usernum" value="admin"/>
				</div>
				<div class="form-group">
					<label for="password">비밀번호</label> <input type="password"
						id="userpw" name="userpw" value="1234"/>
				</div>
				<button class="btn" id="btnLogin" type="button">Login</button>
			</form>
			<div class="welcome-text">ezen소프트웨어에 오신 것을 환영합니다.</div>
		</div>
	</div>
</body>
</html>