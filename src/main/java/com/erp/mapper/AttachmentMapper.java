package com.erp.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentMapper {
	boolean isReferenced(String name);
}
