package com.erp.vo;

public class ChatMessageVO {

	private long messageNo;
	private long roomNo;
	private String senderUsernum;
	private String content;
	private String sentAt;
	private String readAt;

	public long getMessageNo() { return messageNo; }
	public long getRoomNo() { return roomNo; }
	public String getSenderUsernum() { return senderUsernum; }
	public String getContent() { return content; }
	public String getSentAt() { return sentAt; }
	public String getReadAt() { return readAt; }

	public void setMessageNo(long messageNo) { this.messageNo = messageNo; }
	public void setRoomNo(long roomNo) { this.roomNo = roomNo; }
	public void setSenderUsernum(String senderUsernum) { this.senderUsernum = senderUsernum; }
	public void setContent(String content) { this.content = content; }
	public void setSentAt(String sentAt) { this.sentAt = sentAt; }
	public void setReadAt(String readAt) { this.readAt = readAt; }
}
