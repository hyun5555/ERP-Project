/*
 * 모듈명 : 테이블 approval_file의 데이터를 표현하기 위한 클래스
 * 작성일 : 2025.04.11
 * 작성자 : 손현아
 */
package com.erp.vo;

public class approval_file_VO {
	
	private int approval_no;    //전자결재 번호
	private String apname;      //첨부파일 물리명
	private String afname;      //첨부파일 논리명
	
	
	public int getApproval_no()    {return approval_no; }
	public String getApname()      {return apname;	    }
	public String getAfname()      {return afname;	    }
	
	
	public void setApproval_no(int approval_no)    {this.approval_no = approval_no;}
	public void setApname(String apname)           {this.apname      = apname;	   }
	public void setAfname(String afname)           {this.afname      = afname;	   }
	
	
}
