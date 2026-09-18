package com.erp.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erp.mapper.ChatMapper;
import com.erp.vo.ChatMessageVO;
import com.erp.vo.ChatRoomVO;
import com.erp.vo.userVO;

@Service
public class ChatService {

	private static final int MAX_MESSAGE_LENGTH = 2000;

	private final ChatMapper chatMapper;
	private final UserService userService;

	public ChatService(ChatMapper chatMapper, UserService userService) {
		this.chatMapper = chatMapper;
		this.userService = userService;
	}

	@Transactional
	public long openRoom(String usernum, String partnerUsernum) {
		if (partnerUsernum == null || partnerUsernum.isBlank() || usernum.equals(partnerUsernum)) {
			throw new IllegalArgumentException("대화 상대를 확인해 주세요.");
		}
		userVO partner = userService.getUser(partnerUsernum);
		if (!"재직".equals(partner.getUser_status())) {
			throw new IllegalArgumentException("재직 중인 사원과만 대화할 수 있습니다.");
		}

		String userA = usernum.compareTo(partnerUsernum) < 0 ? usernum : partnerUsernum;
		String userB = usernum.compareTo(partnerUsernum) < 0 ? partnerUsernum : usernum;
		chatMapper.insertRoom(userA, userB);
		return chatMapper.findRoomNo(userA, userB);
	}

	public List<ChatRoomVO> getRooms(String usernum) {
		return chatMapper.selectRooms(usernum);
	}

	public List<userVO> getContacts(String usernum) {
		return chatMapper.selectContacts(usernum);
	}

	public List<ChatMessageVO> getMessages(long roomNo, String usernum) {
		requireParticipant(roomNo, usernum);
		return chatMapper.selectMessages(roomNo);
	}

	@Transactional
	public ChatMessageVO sendMessage(long roomNo, String senderUsernum, String content) {
		requireParticipant(roomNo, senderUsernum);
		String normalized = content == null ? "" : content.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException("메시지는 1자 이상 2,000자 이하로 입력해 주세요.");
		}

		ChatMessageVO message = new ChatMessageVO();
		message.setRoomNo(roomNo);
		message.setSenderUsernum(senderUsernum);
		message.setContent(normalized);
		chatMapper.insertMessage(message);
		chatMapper.touchRoom(roomNo);
		return chatMapper.selectMessage(message.getMessageNo());
	}

	@Transactional
	public int markRead(long roomNo, String reader) {
		requireParticipant(roomNo, reader);
		return chatMapper.markRead(roomNo, reader);
	}

	public String getOtherParticipant(long roomNo, String usernum) {
		return requireParticipant(roomNo, usernum);
	}

	private String requireParticipant(long roomNo, String usernum) {
		String other = chatMapper.findOtherParticipant(roomNo, usernum);
		if (other == null) {
			throw new AccessDeniedException("이 대화방에 접근할 수 없습니다.");
		}
		return other;
	}
}
