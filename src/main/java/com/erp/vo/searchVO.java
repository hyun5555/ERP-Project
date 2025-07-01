package com.erp.vo;

public class searchVO 
{
	private int    pageno;      //페이지 번호
	private int    offset;		//시작위치
	private String notice_team; //부서명
	private String searchKey;   //검색(제목,내용, name, usernum)
	private String searchWord;	//검색어
	private String team;		//부서명
	private String level;       //직급
	private String user_status; //상태
	

	public int    getPageno()      { return pageno;     } 
	public int    getOffset()      { return offset;     }
	public String getNotice_team() { return notice_team;}
	public String getSearchKey()   { return searchKey;  }
	public String getSearchWord()  { return searchWord; }
	public String getTeam()        {return team;	    }
	public String getLevel()       {return level;	    }
	public String getUser_status() {return user_status;	}
	
	

	public void setNotice_team(String notice_team) { this.notice_team = notice_team; }
	public void setSearchKey(String searchKey)     { this.searchKey = searchKey;     }
	public void setSearchWord(String searchWord)   { this.searchWord = searchWord;   }
	public void setOffset(int offset)              {this.offset = offset;	         }
	public void setTeam(String team)               {this.team = team;	             }
	public void setLevel(String level)             {this.level = level;	             }
	public void setUser_status(String user_status) {this.user_status = user_status;	 }
	public void setPageno(int pageno) {
		this.pageno = pageno;
		this.offset = (this.pageno - 1) * 10;
	}
}

