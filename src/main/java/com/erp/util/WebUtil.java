package com.erp.util;

public class WebUtil 
{
	public static int pageOffset(int page, int pageSize) {
		long offset = ((long) Math.max(page, 1) - 1) * pageSize;
		if (offset > Integer.MAX_VALUE) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST, "페이지 번호가 지원 범위를 초과했습니다.");
		}
		return (int) offset;
	}

	//일반 텍스트 내용를 HTML 내용으로 변환
	//매개변수 : text - 일반 텍스트 내용
	//리턴값 : 변환된 HTML 내용	
	public static String Text2HTML(String text)
	{
		if (text == null) {
            return "";  // null인 경우 빈 문자열을 반환
        }
		
		String note = text;
		//< 와 >를 변경한다.
		note = note.replace("<","&lt;");
		note = note.replace(">","&gt;");

		//엔터문자를 변경한다.
		note = note.replace("\n","<br>\n");
		return note;
	}
}
