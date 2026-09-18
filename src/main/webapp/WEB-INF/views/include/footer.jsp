<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
			<!------------------------------------------------------------ footer 시작---------------------------------------------------------------------->
			<main class="page-content">
				<footer class="py-3 my-4">
					<ul class="footer-nav d-flex align-items-center border-bottom pb-3 mb-3 custom-footer">
						<li class="nav-item home-icon"><a href="/ERP/main.do"
							class="nav-link text-body-secondary text-start"> <i
								class="fa-solid fa-house"></i>
						</a></li>
						<div class="d-flex flex-grow-1 justify-content-center flex-wrap center-items">
							<li class="nav-item mx-3 d-flex align-items-center">
								<div class="nav-link text-body-secondary text-center">C조
									프로젝트 : 기업 전자결재 웹 서비스</div>
							</li>
						</div>
						<li class="nav-item address-text d-flex align-items-center">
							<div class="nav-link text-body-secondary">주소 : 전북 전주시 덕진구 백제대로 572 5층
							</div>
						</li>
						<li class="nav-item mx-3 d-flex align-items-center">
							<a href="https://sunjung0000.cafe24.com/" class="nav-link text-body-secondary text-center">EZEN</a>
						</li>
					</ul>
					<p class="text-center text-body-secondary">© 2025 EZEN Company</p>
				</footer>
			</main>
			<!------------------------------------------------------------ footer 끝남---------------------------------------------------------------------->
		</div>
		<div class="legacy-floating-actions">
			<button type="button" data-theme-toggle aria-label="다크 모드 전환"><span aria-hidden="true">◐</span></button>
			<button type="button" class="legacy-chat-button" data-chat-toggle aria-label="AI 챗봇 열기"><span aria-hidden="true">✦</span></button>
		</div>
		<aside class="legacy-chat-panel" data-chat-panel aria-label="AI 업무 도우미" hidden>
			<header><strong>AI 업무 도우미</strong><button type="button" data-chat-close aria-label="닫기">×</button></header>
			<p>사내 문서 검색과 결재 요약을 지원할 예정입니다.</p>
			<small>로컬 LLM API 연결 준비 중</small>
		</aside>
		<script>
			jQuery(function($) {
				$("#close-sidebar").click(function() {
					$(".page-wrapper").removeClass("toggled");
				});
				$("#show-sidebar").click(function() {
					$(".page-wrapper").addClass("toggled");
				});
	
				$('#fileInput').change(function() {
					if (this.files.length > 0) {
						$('#fileName').text(this.files[0].name);
					} else {
						$('#fileName').text('파일이 선택되지 않았습니다');
					}
				});
				
				window.removeFile = function() {
					$('#fileInput').val('');
					$('#fileName').text('파일이 선택되지 않았습니다');
				};
	
				$('#selectAll').change(function() {
					$('.emp-del').prop('checked', this.checked);
				});
				
				$(document).ready(function() {
			        $(".click-cell").on("click", function() {
			            window.location.href = "/ERP/user/view.do";
			        });
			    });
				
			});
		</script>
		<script src="/ERP/resources/js/legacy-ui.js"></script>
	</body>
</html>
