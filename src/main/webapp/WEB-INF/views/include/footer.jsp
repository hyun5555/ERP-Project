<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
			<!------------------------------------------------------------ footer 시작---------------------------------------------------------------------->
			<main class="page-content">
				<footer class="py-3 my-4">
					<ul class="footer-nav d-flex align-items-center border-bottom pb-3 mb-3 custom-footer">
						<li class="nav-item home-icon"><a href="/ERP/main.jsp"
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
		<script
			src="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js"></script>
		<script
			src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"
			crossorigin="anonymous"></script>
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
	</body>
</html>
