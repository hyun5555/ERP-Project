package com.erp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.vo.ChatMessageVO;
import com.erp.vo.ChatRoomVO;
import com.erp.vo.userVO;

@Mapper
public interface ChatMapper {

	Long findRoomNo(@Param("userA") String userA, @Param("userB") String userB);

	int insertRoom(@Param("userA") String userA, @Param("userB") String userB);

	String findOtherParticipant(@Param("roomNo") long roomNo,
			@Param("usernum") String usernum);

	List<ChatRoomVO> selectRooms(String usernum);

	List<userVO> selectContacts(String usernum);

	List<ChatMessageVO> selectMessages(long roomNo);

	int insertMessage(ChatMessageVO message);

	ChatMessageVO selectMessage(long messageNo);

	int touchRoom(long roomNo);

	int markRead(@Param("roomNo") long roomNo, @Param("reader") String reader);
}
