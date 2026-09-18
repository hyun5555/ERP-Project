package com.erp.control;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erp.service.ChatService;
import com.erp.vo.ChatMessageVO;
import com.erp.vo.ChatRoomVO;
import com.erp.vo.userVO;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final DateTimeFormatter DATE_TIME =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final ChatService chatService;
	private final SimpMessagingTemplate messagingTemplate;

	public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
		this.chatService = chatService;
		this.messagingTemplate = messagingTemplate;
	}

	@GetMapping("/rooms")
	public List<ChatRoomVO> rooms(Principal principal) {
		return chatService.getRooms(principal.getName());
	}

	@GetMapping("/contacts")
	public List<ContactResponse> contacts(Principal principal) {
		return chatService.getContacts(principal.getName()).stream()
				.map(ContactResponse::from)
				.toList();
	}

	@PostMapping("/rooms")
	public Map<String, Long> openRoom(@RequestBody OpenRoomRequest request, Principal principal) {
		return Map.of("roomNo", chatService.openRoom(principal.getName(), request.partnerUsernum()));
	}

	@GetMapping("/rooms/{roomNo}/messages")
	public List<ChatMessageVO> messages(@PathVariable long roomNo, Principal principal) {
		return chatService.getMessages(roomNo, principal.getName());
	}

	@PostMapping("/rooms/{roomNo}/read")
	public Map<String, Integer> read(@PathVariable long roomNo, Principal principal) {
		String reader = principal.getName();
		String sender = chatService.getOtherParticipant(roomNo, reader);
		int count = chatService.markRead(roomNo, reader);
		if (count > 0) {
			messagingTemplate.convertAndSendToUser(sender, "/queue/read",
					new ReadReceipt(roomNo, reader, LocalDateTime.now().format(DATE_TIME)));
		}
		return Map.of("updated", count);
	}

	@MessageMapping("/chat.send")
	public void send(SendMessageRequest request, Principal principal) {
		String sender = principal.getName();
		String recipient = chatService.getOtherParticipant(request.roomNo(), sender);
		ChatMessageVO message = chatService.sendMessage(request.roomNo(), sender, request.content());
		messagingTemplate.convertAndSendToUser(sender, "/queue/messages", message);
		messagingTemplate.convertAndSendToUser(recipient, "/queue/messages", message);
	}

	@MessageExceptionHandler
	@SendToUser("/queue/errors")
	public Map<String, String> messageError(Exception exception) {
		String message = exception instanceof IllegalArgumentException
				? exception.getMessage() : "메시지를 처리할 수 없습니다.";
		return Map.of("message", message);
	}

	public record OpenRoomRequest(String partnerUsernum) {}
	public record SendMessageRequest(long roomNo, String content) {}
	public record ReadReceipt(long roomNo, String readerUsernum, String readAt) {}
	public record ContactResponse(String usernum, String name, String team, String level) {
		static ContactResponse from(userVO user) {
			return new ContactResponse(user.getUsernum(), user.getName(), user.getTeam(), user.getLevel());
		}
	}
}
