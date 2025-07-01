$(document).ready(function () 
{
	$('#doc_select').on('change', function () 
	{
		if ($(this).val() !== '문서구분') 
		{
			$('#approval_code').focus();
		}
	});

	$('#approval_code').on('keydown', function (e) 
	{
		if (e.key === 'Enter' || e.keyCode === 9) 
		{
			e.preventDefault();
			$('#approval-title').focus();
		}
	});

	$('#approval-title').on('keydown', function (e) 
	{
		if (e.key === 'Enter' || e.keyCode === 9) 
		{
			e.preventDefault();
			$('#approval-note').focus();
		}
	});

	$("#writeComplate").click(function (e) 
	{
		e.preventDefault();
		DoSubmitApproval();
	});

	$(document).on("click", ".member", function () 
	{
		const userNum = $(this).data("usernum");
		const userName = $(this).data("username");
		const userTeam = $(this).data("team");
		const avatarText = $(this).find('.cteam_circle_avatar').text();

		if ($("#approval-add").prevAll(`#selected-${userNum}`).length > 0) 
		{
			alert("이미 있는 사원입니다.");
			return;
		}

		const index = $("#approval_target").length;

		const cardHtml = `
			<div class="cteam_person_card2" id="selected-${userNum}" data-usernum="${userNum}">
				<div class="person_info_box">
					<div class="cteam_circle_avatar">${avatarText}</div>
					<div class="info">
						<strong>${userName}</strong><br> ${userTeam}
					</div>
				</div>
				<button type="button" class="cteam_btn_style approval-del" data-usernum="${userNum}">삭제</button>
			    <input type="hidden" id="approval_target" name="approval_target" value="${userNum}">
			    <input type="hidden" id="approval_sort" name="approval_sort" value="${index + 1}">
			</div>
		`;
		const $cardElement = $(cardHtml);

		// `+` 버튼 앞에 추가
		$('#approval-add').before($cardElement);
		
		// 모달 닫기
		$('#exampleModal').modal('hide');
	});

	function reindexApprovalLines() {
	  $('#selectedUsers .cteam_person_card2').each(function(index) {
	    $(this).find('input[name^="approvalLines"]').each(function() {
	    	const field = $(this).attr('name').split('.').pop(); // approval_target or approval_sort
	     	$(this).attr('name', `approvalLines[${index}].${field}`);
	      if (field === 'approval_sort') {
	        $(this).val(index + 1);
	      }
	    });
	  });
	}

	$(document).on("click", ".approval-del", function () 
	{
		const userNum = $(this).data("usernum");
		$(`#selected-${userNum}`).remove();
		reindexApprovalLines();
	});
});


function setFileName(obj)
{
	fileName = $(obj).val();
	//파일 이름만 얻기
	pos = fileName.lastIndexOf("\\");
	fileName = fileName.substr(pos+1); 
	$(obj).parent().find("#fileName").html(fileName);
	
	//입력항목을 추가한다.
	html = "";
	html += "<div class='cteam_file_line' style='width:90%'>";
	html += "	<input type='file' id='attach' name='attach' onchange='setFileName(this);' style='display: none;'/> ";
	html += "	<span id='fileName' style='width:90%'>파일이 선택되지 않았습니다</span>";
	html += "	<button type='button' id='file-add' name='file-add' onclick='addAttach(this);'>추가</button>";
	html += "	<button type='button' id='file-del' name='file-del' onclick='removeAttach(this);'>삭제</button>";
	html += "</div>";
	
	//cteam_form_group 찾기
	$(obj).parent().parent().append(html);
}

function addAttach(obj)
{
	
	$(obj).parent().find("#attach").click();
}
function removeAttach(obj) 
{
	all = $(obj).parent().parent().find(".cteam_file_line");

	if(all.length <= 1)
	{
		//첨부파일이 1개이므로 삭제 불가
		return;	
	}
	$(obj).parent().remove();
}	


function DoSubmitApproval() 
{
	if ($("#doc_select").val() == "문서구분")
	{
		alert("문서구분을 선택하세요.");
		$("#doc_select").focus();
		return;
	}

	if ($("#approval_code").val() == "") 
	{
		alert("품의번호를 입력하세요.");
		$("#approval_code").focus();
		return;
	}
	if ($("#approval-title").val() == "") 
	{
		alert("제목을 입력하세요.");
		$("#approval-title").focus();
		return;
	}
	if ($("#approval-note").val() == "") 
	{
		alert("내용을 입력하세요.");
		$("#approval-note").focus();
		return;
	}
	if ($('#approval-add').prevAll('.cteam_person_card2').length === 0)
	{
		alert("결재선이 없습니다.");
		return;
	}

	if (confirm("등록하시겠습니까?")) 
	{
		$("#writeOK").submit();
	}
}
