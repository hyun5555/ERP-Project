<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<meta name="theme-color" content="#8bcff0">
	<meta name="description" content="전자결재, 공지, 사원관리와 로컬 AI 업무 지원을 한곳에서 제공하는 사내 업무 시스템">
	<meta name="csrf-parameter" content="${_csrf.parameterName}">
	<meta name="csrf-header" content="${_csrf.headerName}">
	<meta name="csrf-token" content="${_csrf.token}">
	<title>EZEN Works</title>
	<script>document.documentElement.dataset.theme = localStorage.getItem('erp-theme') || 'light';</script>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/app/app.css">
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/workspace-shell.css">
</head>
<body>
	<div id="root"><div class="app-loading">업무 공간을 불러오는 중입니다.</div></div>
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
	<script type="module" src="${pageContext.request.contextPath}/resources/app/app.js"></script>
</body>
</html>
