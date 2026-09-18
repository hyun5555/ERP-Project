<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%
String menu = (String)pageContext.getAttribute("menu");
if(menu == null) menu = "";
%>
<!DOCTYPE html>
<html lang="ko">
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title>EZEN WORKS</title>
		<link href="https://use.fontawesome.com/releases/v6.7.2/css/all.css"
			rel="stylesheet">
		<link rel="stylesheet" href="/ERP/resources/css/common.css">
		<link rel="stylesheet" href="/ERP/resources/css/responsive.css">
		<link rel="stylesheet" href="/ERP/resources/css/workspace-shell.css">
		<script>document.documentElement.dataset.theme = localStorage.getItem('erp-theme') || 'light';</script>
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
				}
			});				
		}); 
		</script>
		<c:if test="${ sessionScope.loginUser == null }">
			<script>
				document.location = "/ERP/login/login.do";
			</script>
		</c:if>	
		<div class="page-wrapper chiller-theme workspace-shell">
			<!----------------------------------------------------사이드바 시작 --------------------------------------------------------------------->
			<button id="show-sidebar" class="workspace-menu-button" type="button" aria-label="메뉴 열기">
				<svg class="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
			</button>
			<aside id="sidebar" class="sidebar workspace-sidebar">
				<div class="sidebar-heading">
					<a class="brand" href="/ERP/main.do">EZEN</a>
					<button id="close-sidebar" class="icon-button sidebar-close" type="button" aria-label="메뉴 닫기">
						<svg class="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>
					</button>
				</div>
				<div class="sidebar-profile">
					<span class="avatar"><c:choose><c:when test="${fn:length(loginUser.name) > 1}">${fn:substring(loginUser.name, fn:length(loginUser.name) - 2, fn:length(loginUser.name))}</c:when><c:otherwise>${loginUser.name}</c:otherwise></c:choose></span>
					<div>
							<strong>${ loginUser.name }</strong>
							<small>ezen소프트웨어</small>
							<small>${ loginUser.team } · ${ loginUser.level }</small>
					</div>
					<div class="sidebar-profile-actions">
							<a href="/ERP/user/myinfo.do" onclick="window.open(this.href, 'MyInfo', 'width=900,height=700'); return false;">내 정보</a>
							<form action="/ERP/login/logout.do" method="post">
								<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
								<button type="submit">로그아웃</button>
							</form>
					</div>
				</div>
				<nav class="main-nav" aria-label="주요 메뉴">
					<p class="nav-label">내 결재관리</p>
					<a class="nav-link <%= "1".equals(request.getParameter("mode")) ? "active" : "" %>" href="/ERP/approval/list.do?mode=1"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 2h9l4 4v16H6z"/><path d="M14 2v5h5"/><path d="M9 12h6M9 16h6"/></svg><span>결재대기</span><b id="docWait">0</b></a>
					<a class="nav-link <%= "2".equals(request.getParameter("mode")) ? "active" : "" %>" href="/ERP/approval/list.do?mode=2"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 2h9l4 4v16H6z"/><path d="M14 2v5h5"/><path d="M9 12h6M9 16h6"/></svg><span>결재반려</span><b id="docReturn">0</b></a>
					<a class="nav-link <%= "3".equals(request.getParameter("mode")) ? "active" : "" %>" href="/ERP/approval/list.do?mode=3"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 2h9l4 4v16H6z"/><path d="M14 2v5h5"/><path d="M9 12h6M9 16h6"/></svg><span>결재진행</span><b id="docProc">0</b></a>
					<a class="nav-link <%= "4".equals(request.getParameter("mode")) ? "active" : "" %>" href="/ERP/approval/list.do?mode=4"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 2h9l4 4v16H6z"/><path d="M14 2v5h5"/><path d="M9 12h6M9 16h6"/></svg><span>결재승인</span><b id="docOK">0</b></a>
					<a class="nav-link <%= menu.equals("recv") ? "active" : "" %>" href="/ERP/approval/recv.do"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 5h16v14H4z"/><path d="m4 13 4-4h8l4 4"/><path d="M8 13h8"/></svg><span>결재수신</span><b id="docRecv">0</b></a>
					<a class="nav-link <%= menu.equals("write") ? "active" : "" %>" href="/ERP/approval/write.do"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13 7 4 4"/></svg><span>결재 작성</span></a>
					<a class="nav-link <%= menu.equals("allok") ? "active" : "" %>" href="/ERP/approval/allok.do"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="m5 12 4 4L19 6"/></svg><span>전체 승인 내역</span></a>
					<a class="nav-link <%= menu.equals("notice") ? "active" : "" %>" href="/ERP/notice/list.do"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg><span>공지사항</span></a>
						<c:if test="${loginUser.authority}">
					<a class="nav-link <%= menu.equals("user-edit") ? "active" : "" %>" href="/ERP/user/list.do"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2"/><path d="M3 20c0-4 2-7 6-7s6 3 6 7M15 14c3 0 5 2 5 5"/></svg><span>사원 관리</span></a>
						</c:if>
				</nav>
			</aside>
			<!----------------------------------------------------사이드바 끝남 --------------------------------------------------------------------->
