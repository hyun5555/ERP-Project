<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
String menu = (String)pageContext.getAttribute("menu");
if(menu == null) menu = "";
%>
<!DOCTYPE html>
<html lang="en">
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title>EZEN 전자결재</title>
		<link
			href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css"
			rel="stylesheet">
		<link
			href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
			rel="stylesheet" crossorigin="anonymous">
		<link href="https://use.fontawesome.com/releases/v6.7.2/css/all.css"
			rel="stylesheet">
		<link href="/ERP/resources/css/sidebar.css" rel="stylesheet" type="text/css">
		<link rel="stylesheet" href="/ERP/resources/css/common.css">
		<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
	</head>
	<body>
		<script>
		$(function()
		{
			$.ajax({
				type: "get",
				url : "/ERP/approval/count.do",
				dataType: "html",
				success : function(data){
					// 통신이 성공적으로 이루어졌을때 이 함수를 타게된다.
					data = data.trim();
					//alert(data);
					//결재대기/결재반려/결재진행/결재승인/결재수신
					count_list = data.split("/");
					$("#docWait").html(count_list[0]);   //결재대기
					$("#docReturn").html(count_list[1]); //결재반려
					$("#docProc").html(count_list[2]);   //결재진행
					$("#docOK").html(count_list[3]);     //결재승인
					$("#docRecv").html(count_list[4]);   //결재수신
					
					total = parseInt(count_list[0]) + parseInt(count_list[1]) + parseInt(count_list[2]) + parseInt(count_list[3]);
					$("#NoticedocAll").html(total);
					$("#NoticedocRecv").html(count_list[4]);   //결재수신
				}			
			});				
		}); 
		</script>
		<c:if test="${ sessionScope.loginUser == null }">
			<script>
				document.location = "/ERP/login/login.do";
			</script>
		</c:if>	
		<div class="page-wrapper chiller-theme toggled">
			<!----------------------------------------------------사이드바 시작 --------------------------------------------------------------------->
			<a id="show-sidebar" class="btn btn-sm btn-dark" href="#">
				<i class="fas fa-bars"></i> <i class="fa fa-bell"></i>
				<span class="badge badge-pill badge-warning notification">3</span>
			</a>
			<nav id="sidebar" class="sidebar-wrapper">
				<div class="sidebar-content">
					<div class="sidebar-brand">
						<a href="/ERP/main.do">EZEN</a>
						<div id="close-sidebar">
							<i class="fas fa-times"></i>
						</div>
					</div>
					<div class="sidebar-header">
						<div class="user-left">
							<div class="user-pic">
								<img src="/ERP/resources/img/usericon.png" alt="사용자 이미지가 없습니다">
							</div>
							<div class="user-name">
								<strong>${ loginUser.name }</strong>
							</div>
							<button type="button" class="sidebar-myinfo-btn" 
							onclick="window.open('/ERP/user/myinfo.do?usernum=${ loginUser.usernum }', 'MyInfo', 'width=900,height=700')">내 정보</button>
						</div>
						<div class="user-right">
							<div class="user-details">
								<div class="user-team">ezen소프트웨어</div>
								<div class="emp-num">${ loginUser.team }</div>
								<div class="emp-num">${ loginUser.usernum }</div>
								<button type="button" class="sidebar-logout-btn" onclick="location.href='/ERP/login/login.do'">로그아웃</button>
							</div>
						</div>
					</div>
					<div class="sidebar-menu">
						<ul>
							<li class="header-menu  <%= menu.equals("list")%>"><a href="/ERP/approval/list.do?mode=0">
							<span>내 결재관리</span></a></li>
							<div class="menu-bar"></div>
							<li class="sidebar-submenu <%= "1".equals(request.getParameter("mode")) ? "active" : "" %>"><a href="/ERP/approval/list.do?mode=1"> <i
									class="fa-solid fa-user-clock active"></i> <span>결재대기</span> <span
									class="badge badge-pill badge-secondary" id="docWait"></span></a></li>
							<li class="sidebar-submenu <%= "2".equals(request.getParameter("mode")) ? "active" : "" %>"><a href="/ERP/approval/list.do?mode=2"> <i
									class="fa-solid fa-user-xmark"></i> <span>결재반려</span> <span
									class="badge badge-pill badge-danger" id="docReturn">3</span></a></li>
							<li class="sidebar-submenu <%= "3".equals(request.getParameter("mode")) ? "active" : "" %>"><a href="/ERP/approval/list.do?mode=3"> <i
									class="fa-solid fa-list-check"></i> <span>결재진행</span> <span
									class="badge badge-pill badge-info" id="docProc">0</span></a></li>
							<li class="sidebar-submenu <%= "4".equals(request.getParameter("mode")) ? "active" : "" %>"><a href="/ERP/approval/list.do?mode=4"> <i
									class="fa-solid fa-user-check"></i> <span>결재승인</span> <span
									class="badge badge-pill badge-primary" id="docOK">0</span></a></li>
							<li class="sidebar-submenu <%= menu.equals("recv") ? "active" :"" %>"><a href="/ERP/approval/recv.do"> <i
									class="fa-solid fa-envelope-open-text"></i> <span>결재수신</span> <span
									class="badge badge-pill badge-success" id="docRecv">0</span></a></li>
							<li class="sidebar-submenu <%= menu.equals("write") ? "active" :"" %>">
							<a href="/ERP/approval/write.do"> <i
									class="fa-solid fa-file-pen"></i> <span>결재작성</span></a></li>
							<div class="menu-bar"></div>
							<li class="sidebar-submenu <%= menu.equals("allok") ? "active" :"" %>"><a href="/ERP/approval/allok.do"> <i
									class="fa-solid fa-layer-group"></i> <span>전체 승인 내역</span></a></li>
							<div class="menu-bar"></div>
							<li class="sidebar-submenu <%= menu.equals("notice") ? "active" :"" %>"><a href="/ERP/notice/list.do"> <i
									class="fa-solid fa-bullhorn"></i> <span>공지사항</span></a></li>
							<div class="menu-bar"></div>
							<li class="sidebar-submenu <%= menu.equals("user-edit") ? "active" :"" %>"><a href="/ERP/user/list.do"> <i
									class="fa-solid fa-users-line"></i> <span>사원관리</span></a>
							</li>
						</ul>
					</div>
				</div>
				<div class="sidebar-footer">
					<a href="#"> <i class="fa fa-bell"></i> 
					<span class="badge badge-pill badge-warning notification" id="NoticedocAll">0</span></a>
					<a href="/ERP/approval/recv.do"> <i class="fa fa-envelope"></i>
					<span class="badge badge-pill badge-success notification" id="NoticedocRecv">0</span></a>
				</div>
			</nav>
			<!----------------------------------------------------사이드바 끝남 --------------------------------------------------------------------->
			