/*
 * 모듈명 : 테이블 approval의 데이터를 표현하기 위한 클래스
 * 작성일 : 2025.04.11
 * 작성자 : 손현아
 */
package com.erp.vo;

public class approvalVO {
	
	private int    approval_no;      //전자결재 번호
	private String kind;             //문서구분
	private String writedate;        //작성일자
	private String approval_title;   //전자결재 제목
	private String approval_content; //전자결재 내용
	private String document_status;  //문서결재 상태
	private String usernum;          //사원번호 : 작성자(usernum)는 user에서 FOREIGN KEY로 가져올거임.
	private String approval_code;    //품의번호
	private userVO drafter;			 //유저 정보 객체 : 작성자 정보
	
	public int getApproval_no()         {return approval_no;        }
	public String getKind()             {return kind;               }
	public String getWritedate()        {return writedate;	        }
	public String getApproval_title()   {return approval_title;	    }
	public String getApproval_content() {return approval_content;   }
	public String getDocument_status()  {return document_status;	}
	public String getUsernum()          {return usernum;	        }
	public String getApproval_code()    {return approval_code;	    }
	public userVO getDrafter()          {return drafter; 			}
	
	
	
	public void setApproval_no(int approval_no)              {this.approval_no      = approval_no;      }
	public void setKind(String kind)                         {this.kind             = kind;	            }
	public void setWritedate(String writedate)               {this.writedate        = writedate;        }
	public void setApproval_title(String approval_title)     {this.approval_title   = approval_title;   }
	public void setApproval_content(String approval_content) {this.approval_content = approval_content;	}
	public void setDocument_status(String document_status)   {this.document_status  = document_status;	}
	public void setUsernum(String usernum)                   {this.usernum 			= usernum;	        }
	public void setApproval_code(String approval_code)       {this.approval_code 	= approval_code;	}
	public void setDrafter(userVO drafter)                   {this.drafter          = drafter;          }
	
	
}
