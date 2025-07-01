<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
pageContext.setAttribute("menu", "main");
%>    
<%@ include file="./include/header.jsp" %>
<!---------------------------------------------------메인페이지 시작----------------------------------------------------------->
<script>
$(function()
{
    var today = new Date();
    var date = new Date();           

    $("#preMon").click(function() 
    { 
    	// 이전달
        $("#calendar > tbody > td").remove();
        $("#calendar > tbody > tr").remove();
        today = new Date ( today.getFullYear(), today.getMonth()-1, today.getDate());
        buildCalendar();
    })
    
    $("#nextMon").click(function()
    { 
    	//다음달
        $("#calendar > tbody > td").remove();
        $("#calendar > tbody > tr").remove();
        today = new Date ( today.getFullYear(), today.getMonth()+1, today.getDate());
        buildCalendar();
    })

    function buildCalendar() 
    {
        nowYear = today.getFullYear();
        nowMonth = today.getMonth();
        firstDate = new Date(nowYear,nowMonth,1).getDate();
        firstDay = new Date(nowYear,nowMonth,1).getDay(); //1st의 요일
        lastDate = new Date(nowYear,nowMonth+1,0).getDate();
        
        $(".days").html("");
        
        if((nowYear%4===0 && nowYear % 100 !==0) || nowYear%400===0) 
        { 
        	//윤년 적용
            lastDate[1]=29;
        }

        $(".current-date").text(nowYear+"년 "+(nowMonth+1)+"월");

        for (i=0; i<firstDay; i++) 
        { 
        	//첫번째 줄 빈칸
        	$(".days").append("<li></li>")
        }
        for (i=1; i <=lastDate; i++)
        { 
        	// 날짜 채우기
            $(".days").append("<li>" + i + "</li>")
        }
        $(".days > li").each(function(index)
        { 
        	if(nowYear==date.getFullYear() && nowMonth==date.getMonth() && $(this).html() == date.getDate())
        	{
        		console.log($(this).html());
        		$(this).addClass("active");
        	}
        }) 
    }
    buildCalendar();
    
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
			$("#MaindocWait").html(count_list[0]);   //결재대기
			$("#MaindocReturn").html(count_list[1]); //결재반려
			$("#MaindocProc").html(count_list[2]);   //결재진행
			$("#MaindocOK").html(count_list[3]);     //결재승인
			$("#MaindocRecv").html(count_list[4]);   //결재수신
		}			
	});	    
})

</script>        
<main class="page-content">
	<div class="row mb-4">
		<div class="col-md-12">
			<div class="documentlist_title">
				<div class="cteam_subtitle">
					<h2 class="cteam_head_maintitle">EZEN
				</div>
			</div>
			<hr>
			<div class="cteam_maindocu_group">
				<label style="margin-top: 10px;">오늘의 결재 현황 &gt</label><br>
				<button class="cteam_btn_style_2" onclick="location.href='/ERP/approval/recv.do'" >내 수신함 <span id="MaindocRecv">0</span>건</button><br>
				<button class="cteam_btn_style" onclick="location.href='/ERP/approval/list.do?mode=1'">대기중 <span id="MaindocWait">0</span>건</button><br>
				<button class="cteam_btn_style" onclick="location.href='/ERP/approval/list.do?mode=3'">진행중 <span id="MaindocProc">0</span>건</button><br>
				<button class="cteam_btn_style" onclick="location.href='/ERP/approval/list.do?mode=2'">반려 <span id="MaindocReturn">0</span>건</button><br>
				<button class="cteam_btn_style" onclick="location.href='/ERP/approval/list.do?mode=4'">승인 <span id="MaindocOK">0</span>건</button><br>
			</div>
			<div class="main_middle_group">
				<div class="calender_contentes">
					<div class="cal_body">
						<header>
							<div class="cal_set">
								<button id="preMon" class="material-icons"> &lt </button>
								<p class="current-date">today</p>
								<button id="nextMon" class="material-icons"> &gt </span>
							</div>
						</header>
							<div class="calendar">
								<ul class="weeks">
									<li>Sun</li>
									<li>Mon</li>
									<li>Tue</li>
									<li>Wed</li>
									<li>Thu</li>
									<li>Fri</li>
									<li>Sat</li>
								</ul>
								<ul class="days">
								</ul>
							</div>
					</div>
				</div>
				<div class="main_notify_box">
					<div class="cteam_left">
					<a href="/ERP/notice/list.do" style="color: inherit; text-decoration: none;"><label class="">공지사항</label></a>
					<label>&gt;</label>	
					</div>
					<c:forEach var="item" items="${list}">
						<div class="cteam_form_group cteam_main_notify">
							<c:if test="${ item.is_important == true }" >
								<label><span class="badge rounded-pill text-bg-danger">중요</span>
							</c:if>
							<a href="/ERP/notice/view.do?notice_no=${ item.notice_no }" style="color: inherit; text-decoration: none;">${ item.notice_title }</a>
							</label>
							<label>( ${ item.noticedate } )</label>
						</div>					
					</c:forEach>
				</div>
			</div>
		</div>
	</div>
</main>

<!----------------------------------------------------메인페이지종료----------------------------------------------------------------------->
<%@ include file="./include/footer.jsp" %>

