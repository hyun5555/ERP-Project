package com.erp.vo;

public class ChatRoomVO {

	private long roomNo;
	private String partnerUsernum;
	private String partnerName;
	private String partnerTeam;
	private String partnerLevel;
	private String lastMessage;
	private String lastMessageAt;
	private int unreadCount;

	public long getRoomNo() { return roomNo; }
	public String getPartnerUsernum() { return partnerUsernum; }
	public String getPartnerName() { return partnerName; }
	public String getPartnerTeam() { return partnerTeam; }
	public String getPartnerLevel() { return partnerLevel; }
	public String getLastMessage() { return lastMessage; }
	public String getLastMessageAt() { return lastMessageAt; }
	public int getUnreadCount() { return unreadCount; }

	public void setRoomNo(long roomNo) { this.roomNo = roomNo; }
	public void setPartnerUsernum(String partnerUsernum) { this.partnerUsernum = partnerUsernum; }
	public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
	public void setPartnerTeam(String partnerTeam) { this.partnerTeam = partnerTeam; }
	public void setPartnerLevel(String partnerLevel) { this.partnerLevel = partnerLevel; }
	public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
	public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }
	public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
