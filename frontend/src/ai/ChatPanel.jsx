import { useState } from 'react'
import { Icon } from '../ui'

export function ChatPanel({ close }) {
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')

  function submit(event) {
    event.preventDefault()
    if (message.trim()) setNotice('로컬 LLM API 연결 후 사용할 수 있습니다.')
  }

  return (
    <aside className="chat-panel" aria-label="AI 업무 도우미">
      <header><div><span className="chat-avatar"><Icon name="chat" /></span><div><strong>AI 업무 도우미</strong><small>Local LLM 준비 중</small></div></div><button className="icon-button" onClick={close} aria-label="챗봇 닫기"><Icon name="close" /></button></header>
      <div className="chat-body"><div className="bot-message">안녕하세요. 사내 문서 검색과 결재 요약을 도와드릴 예정입니다.</div><div className="suggestions"><button onClick={() => setMessage('오늘 처리할 결재 알려줘')}>오늘 처리할 결재</button><button onClick={() => setMessage('최근 공지 요약해줘')}>최근 공지 요약</button></div>{notice && <p className="chat-notice">{notice}</p>}</div>
      <form onSubmit={submit}><input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="업무 관련 질문을 입력하세요" aria-label="챗봇 메시지"/><button aria-label="메시지 보내기">↑</button></form>
    </aside>
  )
}
