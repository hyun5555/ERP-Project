/*
 * 모듈명 : 테이블 approval_line의 데이터를 표현하기 위한 클래스
 * 작성일 : 2025.04.11
 * 작성자 : 손현아
 */

package com.erp.vo;

public class approval_line_VO {
	
	private int    approval_no;     //전자결재 번호
	private String approval_target; //결재대상 : 결재자(usernum)는 user에서 가져올거임.
	private String approval_status; //결재상태
	private String approval_sort;   //결재순서
	private String approval_date;   //결재일자
	private String comment;         //코멘트
	private userVO approver;        //유저 정보 객체 : 결재자 정보
	
	public int getApproval_no()        {return approval_no;     }
	public String getApproval_target() {return approval_target;	}
	public String getApproval_status() {return approval_status;	}
	public String getApproval_sort()   {return approval_sort;	}
	public String getApproval_date()   {return approval_date;	}
	public String getComment()         {return comment;	        }
	public userVO getApprover()        {return approver;        }
	
	public void setApproval_no(int approval_no)            {this.approval_no     = approval_no;     }
	public void setApproval_target(String approval_target) {this.approval_target = approval_target;	}
	public void setApproval_status(String approval_status) {this.approval_status = approval_status;	}
	public void setApproval_sort(String approval_sort)     {this.approval_sort   = approval_sort;	}
	public void setApproval_date(String approval_date)     {this.approval_date   = approval_date;   }
	public void setComment(String comment)                 {this.comment         = comment;	        }
	public void setApprover(userVO approver)               {this.approver        = approver;        }
	

}
