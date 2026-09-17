package com.erp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.vo.userVO;

@Mapper
public interface LoginMapper {

	userVO findByUsernum(String usernum);

	int updatePassword(@Param("usernum") String usernum, @Param("password") String password);

	int changePassword(@Param("usernum") String usernum, @Param("password") String password);
}
