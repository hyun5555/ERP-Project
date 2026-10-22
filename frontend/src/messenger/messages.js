export function mergeMessages(current, incoming) {
  const byId = new Map(current.map((message) => [message.messageNo, message]))
  incoming.forEach((message) => byId.set(message.messageNo, {
    ...message,
    readAt: message.readAt || byId.get(message.messageNo)?.readAt || null
  }))
  return [...byId.values()].sort((a, b) => a.messageNo - b.messageNo)
}

export function mergeRoomHistory(current, history, requestedRoom, selectedRoom) {
  return requestedRoom === selectedRoom ? mergeMessages(current, history) : current
}
