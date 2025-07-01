package com.erp.vo;

public class userVO 
{
	private String  usernum;		//사원번호
	private String  userpw;			//사원비밀번호
	private String  name;			//사원명
	private String  idnum1;			//주민번호앞번호
	private String  idnum2;			//주민번호뒷번호 한자리
	private String  phonenum;		//전화번호
	private String  officenum;		//내선번호
	private String  email;			//이메일
	private String  team;			//부서
	private String  level;			//직급
	private boolean firstlogin;		//처음로그인 true-처음로그인
	private String  joindate;		//입사일자
	private boolean authority;		//권한  true-관리자
	private int     level_num;		//직급번호
	private String  user_status;	//근무상태
	
	public String getUsernum() 	  {return usernum;	 }
	public String getUserpw()	  {return userpw;	 }
	public String getName()		  {return name;		 }
	public String getIdnum1() 	  {return idnum1;	 }
	public String getIdnum2() 	  {return idnum2;	 }
	public String getPhonenum()   {return phonenum;	 }
	public String getOfficenum()  {return officenum; }
	public String getEmail() 	  {return email;	 }
	public String getTeam() 	  {return team;		 }
	public String getLevel() 	  {return level;	 }
	public boolean isFirstlogin() {return firstlogin;}
	public String getJoindate()   {return joindate;	 }
	public boolean isAuthority()  {return authority; }
	public int getLevel_num() 	  {return level_num; }
	public String getUser_status(){return user_status;}
	
	public void setUsernum(String usernum)	  	  {this.usernum    = usernum;   }
	public void setUserpw(String userpw) 		  {this.userpw 	   = userpw;	}
	public void setName(String name) 			  {this.name 	   = name;	 	}
	public void setPhonenum(String phonenum) 	  {this.phonenum   = phonenum;  }
	public void setIdnum1(String idnum1) 		  {this.idnum1      = idnum1;	}
	public void setIdnum2(String idnum2) 		  {this.idnum2      = idnum2;	}
	public void setOfficenum(String officenum)	  {this.officenum  = officenum; }
	public void setEmail(String email) 		      {this.email      = email;	    }
	public void setTeam(String team) 			  {this.team       = team;		}
	public void setLevel(String level) 			  {this.level      = level;		}
	public void setFirstlogin(boolean firstlogin) {this.firstlogin = firstlogin;}
	public void setJoindate(String joindate) 	  {this.joindate   = joindate;	}
	public void setAuthority(boolean authority)   {this.authority  = authority;	}
	public void setLevel_num(int level_num) 	  {this.level_num  = level_num;	}
	public void setUser_status(String user_status){this.user_status = user_status;}
}
