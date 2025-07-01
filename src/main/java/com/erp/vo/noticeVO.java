package com.erp.vo;

public class noticeVO 
{
	private int     notice_no;        //공지사항 번호
	private String  notice_title;	  //공지사항 제목
	private String  notice_content;	  //공지사항 내용
	private boolean is_important;     //중요여부
	private boolean is_main;          //메인노출여부
	private String  pname;    		  //첨부파일 물리명
	private String  fname;			  //첨부파일 논리명
	private String  noticedate;		  //공지일자
	private String  usernum;     	  //작성자 ID
	private String  username;     	  //작성자명
	
	public int getNotice_no() 		  {return notice_no;     }
	public String getNotice_title()   {return notice_title;  }
	public String getNotice_content() {return notice_content;}
	public boolean isIs_important()   {return is_important;  }
	public boolean isIs_main() 		  {return is_main;		 }
	public String getPname() 		  {return pname;		 }
	public String getFname() 		  {return fname;		 }
	public String getnoticedate() 	  {return noticedate;	 }	
	public String getUsernum()        {return usernum;       }
	public String getUsername()       {return username;       }
	
	
	public void setNotice_no(int notice_no)				 {this.notice_no  	  = notice_no;	   }
	public void setNotice_title(String notice_title) 	 {this.notice_title   = notice_title;  }
	public void setNotice_content(String notice_content) {this.notice_content = notice_content;}
	public void setIs_important(boolean is_important) 	 {this.is_important   = is_important;  }
	public void setIs_main(boolean is_main) 			 {this.is_main 		  = is_main;	   }
	public void setPname(String pname) 					 {this.pname 		  = pname;		   }
	public void setFname(String fname) 					 {this.fname 		  = fname;		   }
	public void setnoticedate(String noticedate) 		 {this.noticedate	  = noticedate;	   }
	public void setUsernum(String usernum)               {this.usernum        = usernum;       }
	public void setUsername(String username)             {this.username       = username;      }
	
	public void PrintInfo()
	{
		System.out.println("notice_no:" + notice_no);
		System.out.println("notice_title:" + notice_title);
		System.out.println("notice_content:" + notice_content);
		System.out.println("is_important:" + is_important);
		System.out.println("is_main:" + is_main);
		System.out.println("pname:" + pname);
		System.out.println("fname:" + fname);
		System.out.println("noticedate:" + noticedate);
		System.out.println("usernum:" + usernum);
		System.out.println("username:" + username);

	}
	
}
