drop database erp;
create database erp;
use erp;
-- =======================================유저 테이블================================================
create table user (
    usernum VARCHAR(50) PRIMARY KEY, -- 사원번호: YYYYNNN (년도일련번호)
    userpw VARCHAR(50) NOT NULL,     -- 사원비밀번호
    name VARCHAR(20) NOT NULL,       -- 사원명
    idnum1 VARCHAR(50),  			 -- 주민번호앞번호
    idnum2 VARCHAR(1),  			 -- 주민번호뒷번호 한자리
    phonenum VARCHAR(20),  			 -- 전화번호 
    officenum VARCHAR(20),  		 -- 내선번호 
    email VARCHAR(100),  			 -- 이메일
    team VARCHAR(50),  				 -- 부서 : 100:개발, 200:디자인, 300:경영지원
    level VARCHAR(50), 				 -- 직급 : 100:사장, 200:팀장, 300:대리, 400:사원
    firstlogin BOOLEAN, 			 -- 처음로그인 :true:처음 로그인, false:기존 로그인했었음
    joindate DATETIME default now(), -- 입사일자
    authority BOOLEAN, 				 -- 권한 : true:있음(관리자) , false:없음(사원)
    level_num INT, 					 -- 직급번호 : 사장:100, 팀장:200, 대리:300, 사원:400
    user_status VARCHAR(50) 		 -- 근무상태 : 1:재직, 2:퇴직, 3:휴직
);
-- =======================================공지사항 테이블================================================
create table notice	
(
    notice_no INT NOT NULL PRIMARY KEY AUTO_INCREMENT, 	-- 공지사항 번호 
    notice_title VARCHAR(255) NOT NULL, 				-- 공지사항 제목 
    notice_content TEXT NOT NULL, 						-- 공지사항 내용 
    is_important BOOLEAN DEFAULT FALSE, 				-- 중요여부 : true:중요표시 됨 , false:중요표시 안됨(기본설정)
    is_main BOOLEAN DEFAULT FALSE, 						-- 메인노출여부 : true:메인노출 됨 , false:메인노출 안됨(기본설정) 
    pname VARCHAR(255), 								-- 첨부파일 물리명 
    fname VARCHAR(255), 								-- 첨부파일 논리명 
    noticedate DATETIME default now(),			 		-- 공지일자
    usernum VARCHAR(50),							    -- 작성자 
    FOREIGN KEY (usernum) REFERENCES user(usernum)
);


-- =======================================공지사항 팀 분류 테이블================================================
create table notice_team
(	
	notice_no INT NOT NULL, 					        -- 공지번호
	notice_team VARCHAR(50), 					        -- 공지 대상 부서 : 전체:999, 100:개발, 200:디자인, 300:경영지원 
    FOREIGN KEY (notice_no) REFERENCES notice(notice_no) ON DELETE CASCADE
);
-- =======================================전자결재 테이블================================================
drop table approval_line;
drop table approval_file;
drop table approval;
CREATE TABLE approval (
    approval_no INT PRIMARY KEY AUTO_INCREMENT,  -- 전자결재 번호
    kind VARCHAR(50), 							 -- 문서구분 : 1:연차신청서, 2:품의서, 3:기안서
    writedate DATETIME default now(),			 -- 작성일자
    approval_title VARCHAR(255), 				 -- 전자결재 제목
    approval_content TEXT,						 -- 전자결재 내용
    document_status VARCHAR(255), 			     -- 문서결재 상태 : 1:대기중(승인없음), 2:반려, 3:진행중(부분승인), 4:승인(전결) 문자열로 수정
    usernum VARCHAR(50), 						 -- 사원번호 : 작성자(usernum)는 user에서 FOREIGN KEY로 가져올거임.
    approval_code VARCHAR(255), 							 -- 품의번호 :YYYYNNN (연월일-일련번호)
    FOREIGN KEY (usernum) REFERENCES user(usernum) ON DELETE CASCADE
);
-- =======================================결재 파일 테이블================================================
CREATE TABLE approval_file (
    approval_no INT,   							-- 전자결재 번호
    apname VARCHAR(255), 						-- 첨부파일 물리명
    afname VARCHAR(255), 						-- 첨부파일 논리명
    PRIMARY KEY (approval_no, apname),
    FOREIGN KEY (approval_no) REFERENCES approval(approval_no) ON DELETE CASCADE
);
-- =======================================결재선 테이블================================================
CREATE TABLE approval_line (
    approval_no INT, 							-- 전자결재 번호
    approval_target VARCHAR(20),				-- 결재대상 : 결재자(usernum)는 user에서 FOREIGN KEY로 가져올거임.
    approval_status VARCHAR(255),		        -- 결재상태 : 1:결재대기 , 2:반려, 3:승인 문자열로 수정
    approval_sort INT, 							-- 결재순서
    approval_date DATETIME default now(),		-- 결재일자
    comment VARCHAR(50), 						-- 코멘트
    PRIMARY KEY (approval_no, approval_target),
    FOREIGN KEY (approval_target) REFERENCES user(usernum) ON DELETE CASCADE,
    FOREIGN KEY (approval_no) REFERENCES approval(approval_no) ON DELETE CASCADE
);
-- =======================================최초 관리자 등록================================================
insert into user(usernum, userpw, name, idnum1, idnum2, phonenum, officenum, email, team, level, firstlogin, authority, level_num, user_status)
values ('admin', md5('1234'),'관리자', '123456', '1', '0101231234', '0811231234', 'ezen', '1', '1', true, true, '1', '1');



insert into user(usernum, userpw, name, team, level_num)
values ('2005001', md5('1234'),'김사장', null, '100');

insert into user(usernum, userpw, name, team, level_num)
values ('2005002', md5('1234'),'김팀장', '개발', '200');

insert into user(usernum, userpw, name, team, level_num)
values ('2005003', md5('1234'),'김대리', '개발', '300');

insert into user(usernum, userpw, name, team, level_num)
values ('2005004', md5('1234'),'김사원', '개발', '400');

insert into user(usernum, userpw, name, team, level_num)
values ('2005005', md5('1234'),'박팀장', '디자인', '200');

insert into user(usernum, userpw, name, team, level_num)
values ('2005006', md5('1234'),'박대리', '디자인', '300');

insert into user(usernum, userpw, name, team, level_num)
values ('2005007', md5('1234'),'박사원', '디자인', '400');

insert into user(usernum, userpw, name, team, level_num)
values ('2005008', md5('1234'),'오팀장', '경영지원', '200');

insert into user(usernum, userpw, name, team, level_num)
values ('2005008', md5('1234'),'오대리', '경영지원', '300');

insert into user(usernum, userpw, name, team, level_num)
values ('2005010', md5('1234'),'오사원', '경영지원', '400');


update user set  authority = false where authority  is null;




