package com.erp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.vo.searchVO;
import com.erp.vo.userVO;

@Mapper
public interface UserMapper {

	int insertUser(userVO user);

	List<userVO> selectUsers(searchVO search);

	int countUsers(searchVO search);

	userVO selectUser(String usernum);

	String nextUsernum(@Param("year") String year);

	int updateUser(userVO user);

	int deleteUser(String usernum);

	int deleteUsers(@Param("usernums") List<String> usernums);
}
