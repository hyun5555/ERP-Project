import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { BASE, getJson, postJson } from '../api'
import { Icon, initials } from '../ui'
import { mergeMessages, mergeRoomHistory } from './messages'

export function Messenger({ user, csrf, open, toggle }) {
  const clientRef = useRef(null)
  const selectedRoomRef = useRef(null)
  const openRef = useRef(open)
  const messageEndRef = useRef(null)
  const readThroughRef = useRef(new Map())
  const [rooms, setRooms] = useState([])
  const [contacts, setContacts] = useState([])
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [messages, setMessages] = useState([])
  const [partner, setPartner] = useState('')
  const [content, setContent] = useState('')
  const [status, setStatus] = useState('연결 중')
  const [error, setError] = useState('')

  const loadRooms = () => getJson('/api/chat/rooms').then((data) => { setRooms(data); return data })
  const loadMessages = async (roomNo) => {
    const history = await getJson(`/api/chat/rooms/${roomNo}/messages`)
    if (selectedRoomRef.current?.roomNo === roomNo) {
      setMessages((current) => mergeRoomHistory(current, history, roomNo, selectedRoomRef.current?.roomNo))
    }
  }

  useLayoutEffect(() => { selectedRoomRef.current = selectedRoom; openRef.current = open }, [selectedRoom, open])

  useEffect(() => {
    if (open && selectedRoom) loadMessages(selectedRoom.roomNo).catch((reason) => setError(reason.message))
  }, [open, selectedRoom?.roomNo])

  useEffect(() => {
    const markVisibleMessagesRead = () => {
      if (!openRef.current || !selectedRoom || selectedRoomRef.current?.roomNo !== selectedRoom.roomNo || document.visibilityState !== 'visible') return
      const roomNo = selectedRoom.roomNo
      const latest = messages.reduce((last, message) => message.roomNo === roomNo && message.senderUsernum !== user.usernum && !message.readAt ? Math.max(last, message.messageNo) : last, 0)
      if (!latest || latest <= (readThroughRef.current.get(roomNo) || 0)) return
      readThroughRef.current.set(roomNo, latest)
      postJson(`/api/chat/rooms/${roomNo}/read?throughMessageNo=${latest}`, {}, csrf).then(() => { setError(''); return loadRooms() }).catch((reason) => {
        if (readThroughRef.current.get(roomNo) === latest) readThroughRef.current.delete(roomNo)
        setError(reason.message)
      })
    }
    // Run after the selected conversation has rendered, including reopening a hidden panel.
    markVisibleMessagesRead()
    document.addEventListener('visibilitychange', markVisibleMessagesRead)
    return () => document.removeEventListener('visibilitychange', markVisibleMessagesRead)
  }, [open, selectedRoom?.roomNo, messages, user.usernum, csrf])

  useEffect(() => {
    Promise.all([loadRooms(), getJson('/api/chat/contacts').then(setContacts)])
      .catch((reason) => setError(reason.message))

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}${BASE}/ws`,
      connectHeaders: { [csrf.headerName]: csrf.token },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {}
    })
    client.onConnect = () => {
      client.subscribe('/user/queue/messages', ({ body }) => {
        const message = JSON.parse(body)
        loadRooms().catch(() => {})
        if (selectedRoomRef.current?.roomNo === message.roomNo) {
          setMessages((items) => mergeMessages(items, [message]))
        }
      })
      client.subscribe('/user/queue/read', ({ body }) => {
        const receipt = JSON.parse(body)
        if (selectedRoomRef.current?.roomNo === receipt.roomNo) {
          setMessages((items) => items.map((message) => message.senderUsernum === user.usernum && (receipt.throughMessageNo == null || message.messageNo <= receipt.throughMessageNo) ? { ...message, readAt: message.readAt || receipt.readAt } : message))
        }
      })
      client.subscribe('/user/queue/errors', ({ body }) => setError(JSON.parse(body).message || '메시지를 처리하지 못했습니다.'))
      const ready = client.subscribe('/user/queue/sync', () => {
        ready.unsubscribe()
        setStatus('연결됨')
        setError('')
        loadRooms().catch((reason) => setError(reason.message))
        const current = selectedRoomRef.current
        if (current) loadMessages(current.roomNo).catch((reason) => setError(reason.message))
      })
      client.publish({ destination: '/app/chat.sync', body: '{}' })
    }
    client.onWebSocketClose = () => setStatus('재연결 중')
    client.onStompError = () => setError('메신저 서버에 연결하지 못했습니다.')
    client.activate()
    clientRef.current = client
    return () => { client.deactivate() }
  }, [user.usernum, csrf.headerName, csrf.token])

  useEffect(() => { messageEndRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  function openConversation(room) {
    if (selectedRoomRef.current?.roomNo === room.roomNo) return
    setSelectedRoom(room)
    selectedRoomRef.current = room
    setMessages([])
    setError('')
  }

  async function startConversation(event) {
    event.preventDefault()
    if (!partner) return
    try {
      const { roomNo } = await postJson('/api/chat/rooms', { partnerUsernum: partner }, csrf)
      const freshRooms = await loadRooms()
      openConversation(freshRooms.find((room) => room.roomNo === roomNo))
    } catch (reason) {
      setError(reason.message)
    }
  }

  function send(event) {
    event.preventDefault()
    const message = content.trim()
    if (!message || !selectedRoom) return
    if (!clientRef.current?.connected) {
      setError('서버에 재연결한 뒤 다시 보내 주세요.')
      return
    }
    clientRef.current.publish({ destination: '/app/chat.send', body: JSON.stringify({ roomNo: selectedRoom.roomNo, content: message }) })
    setContent('')
  }

  const unreadCount = rooms.reduce((sum, room) => sum + room.unreadCount, 0)
  return (
    <>
      <button className="floating-button messenger-button" onClick={toggle} aria-label={open ? '사내 메신저 닫기' : '사내 메신저 열기'}><Icon name={open ? 'close' : 'users'} />{unreadCount > 0 && <b>{unreadCount > 99 ? '99+' : unreadCount}</b>}</button>
      {open && <aside className="chat-panel messenger-panel" aria-label="사내 메신저">
        <header><div>{selectedRoom && <button className="messenger-back" onClick={() => { selectedRoomRef.current = null; setSelectedRoom(null) }} aria-label="대화방 목록">←</button>}<span className="chat-avatar"><Icon name="users" /></span><div><strong>{selectedRoom?.partnerName || '사내 메신저'}</strong><small>{status}</small></div></div><button className="icon-button" onClick={toggle} aria-label="메신저 닫기"><Icon name="close" /></button></header>
        {error && <p className="messenger-error" role="alert">{error}</p>}
        {!selectedRoom ? <div className="messenger-lobby">
          <form className="messenger-new" onSubmit={startConversation}><select value={partner} onChange={(event) => setPartner(event.target.value)} aria-label="대화 상대" required><option value="">대화 상대 선택</option>{contacts.map((contact) => <option key={contact.usernum} value={contact.usernum}>{contact.name} · {contact.team} {contact.level}</option>)}</select><button>대화 시작</button></form>
          <div className="messenger-room-list">{rooms.length ? rooms.map((room) => <button key={room.roomNo} onClick={() => openConversation(room)}><span className="avatar small">{initials(room.partnerName)}</span><span><strong>{room.partnerName}</strong><small>{room.lastMessage || '새 대화를 시작해 보세요.'}</small></span><span>{room.lastMessageAt?.slice(5, 16) || ''}{room.unreadCount > 0 && <b>{room.unreadCount}</b>}</span></button>) : <p className="messenger-empty">아직 시작한 대화가 없습니다.</p>}</div>
        </div> : <div className="messenger-conversation">
          <div className="messenger-messages" aria-live="polite">{messages.length ? messages.map((message) => <div key={message.messageNo} className={`messenger-message ${message.senderUsernum === user.usernum ? 'mine' : ''}`}><p>{message.content}</p><small>{message.sentAt?.slice(11, 16)}{message.senderUsernum === user.usernum && ` · ${message.readAt ? '읽음' : '안 읽음'}`}</small></div>) : <p className="messenger-empty">첫 메시지를 보내 보세요.</p>}<span ref={messageEndRef}/></div>
          <form className="messenger-compose" onSubmit={send}><input value={content} onChange={(event) => setContent(event.target.value)} maxLength="2000" placeholder="메시지를 입력하세요" aria-label="메시지"/><button aria-label="메시지 보내기">↑</button></form>
        </div>}
      </aside>}
    </>
  )
}
