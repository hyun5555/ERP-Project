package com.erp.util;

public class WebUtil 
{
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
