import { useEffect, useRef, useState } from 'react'
import { BASE } from '../api'
import { Icon } from '../ui'

const WELCOME = {
  id: 'welcome',
  role: 'assistant',
  text: '안녕하세요. 최근 공지와 내가 처리할 결재를 바탕으로 업무를 도와드릴게요.'
}

export function ChatPanel({ close, csrf }) {
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState([WELCOME])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const abortRef = useRef(null)
  const endRef = useRef(null)

  useEffect(() => () => abortRef.current?.abort(), [])
  useEffect(() => endRef.current?.scrollIntoView({ block: 'end' }), [messages])

  async function submit(event) {
    event.preventDefault()
    const question = message.trim()
    if (!question || loading) return

    const answerId = crypto.randomUUID()
    setMessages((current) => [...current,
      { id: crypto.randomUUID(), role: 'user', text: question },
      { id: answerId, role: 'assistant', text: '' }
    ])
    setMessage('')
    setError('')
    setLoading(true)
    abortRef.current = new AbortController()

    try {
      const response = await fetch(`${BASE}/api/ai/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          [csrf.headerName]: csrf.token
        },
        body: JSON.stringify({ question }),
        signal: abortRef.current.signal
      })
      if (!response.ok) throw new Error(await response.json().then((body) => body.message).catch(() => '질문을 처리하지 못했습니다.'))
      if (!response.body) throw new Error('스트리밍 응답을 열지 못했습니다.')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
        const frames = buffer.split(/\r?\n\r?\n/)
        buffer = frames.pop() || ''
        frames.forEach((frame) => handleEvent(frame, answerId, setMessages, setError))
        if (done) {
          if (buffer.trim()) handleEvent(buffer, answerId, setMessages, setError)
          break
        }
      }
    } catch (caught) {
      if (caught.name !== 'AbortError') {
        const text = caught.message || '로컬 AI가 응답하지 않습니다.'
        setError(text)
        appendAnswer(answerId, text, setMessages)
      }
    } finally {
      setLoading(false)
      abortRef.current = null
    }
  }

  return (
    <aside className="chat-panel" aria-label="AI 업무 도우미">
      <header><div><span className="chat-avatar"><Icon name="chat" /></span><div><strong>AI 업무 도우미</strong><small>{loading ? '답변 생성 중' : 'Local LLM'}</small></div></div><button className="icon-button" onClick={close} aria-label="챗봇 닫기"><Icon name="close" /></button></header>
      <div className="chat-body">
        <div className="chat-messages" aria-live="polite">
          {messages.map((item) => <div key={item.id} className={`chat-message ${item.role === 'user' ? 'user-message' : 'bot-message'}`}><span>{item.text || '답변을 준비하고 있습니다…'}</span>{item.metric && <small>{item.metric}</small>}</div>)}
          <span ref={endRef} />
        </div>
        {messages.length === 1 && <div className="suggestions"><button type="button" onClick={() => setMessage('오늘 처리할 결재 알려줘')}>오늘 처리할 결재</button><button type="button" onClick={() => setMessage('최근 공지 요약해줘')}>최근 공지 요약</button></div>}
        {error && <p className="chat-notice" role="alert">{error}</p>}
      </div>
      <form onSubmit={submit}><input value={message} maxLength="500" disabled={loading} onChange={(event) => setMessage(event.target.value)} placeholder="업무 관련 질문을 입력하세요" aria-label="챗봇 메시지"/><button disabled={loading || !message.trim()} aria-label="메시지 보내기">↑</button></form>
    </aside>
  )
}

function handleEvent(frame, answerId, setMessages, setError) {
  const lines = frame.split(/\r?\n/)
  const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
  const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
  if (!event || !data) return
  const payload = JSON.parse(data)
  if (event === 'token') appendAnswer(answerId, payload.text, setMessages)
  if (event === 'done') {
    const metric = `첫 응답 ${payload.firstTokenMs}ms · 전체 ${payload.durationMs}ms · ${payload.model}`
    setMessages((current) => current.map((item) => item.id === answerId ? { ...item, metric } : item))
  }
  if (event === 'error') {
    setError(payload.message)
    appendAnswer(answerId, payload.message, setMessages)
  }
}

function appendAnswer(answerId, text, setMessages) {
  setMessages((current) => current.map((item) => item.id === answerId
    ? { ...item, text: item.text ? item.text + text : text }
    : item))
}
