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

	userVO selectUserForUpdate(String usernum);

	List<userVO> selectUsersForUpdate(@Param("usernums") List<String> usernums);

	boolean hasBusinessReferences(String usernum);

	int nextUserNumber(@Param("year") String year);

	int ensureUserNumberSequence(@Param("year") String year);

	int selectUserNumberForUpdate(@Param("year") String year);

	String selectLastUsernum(@Param("year") String year);

	int updateUserNumberSequence(@Param("year") String year, @Param("number") int number);

	int updateUser(userVO user);

	int deleteUser(String usernum);

	int deleteUsers(@Param("usernums") List<String> usernums);
}
