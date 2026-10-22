export async function consumeAnswer(response, onEvent) {
  if (response.headers.get('content-type')?.split(';')[0].trim() !== 'text/event-stream') {
    throw new Error('AI 스트리밍 응답 형식이 올바르지 않습니다.')
  }
  if (!response.body) throw new Error('스트리밍 응답을 열지 못했습니다.')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = null
  let donePayload = null
  let visibleAnswer = false

  function frameEvent(frame) {
    const lines = frame.split(/\r?\n/)
    const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
    if (!event || !data) return
    if (terminal) throw new Error('완료된 AI 응답 뒤에 추가 데이터가 도착했습니다.')
    const payload = JSON.parse(data)
    if (event === 'token') {
      if (typeof payload.text !== 'string') throw new Error('AI 응답 내용이 올바르지 않습니다.')
      visibleAnswer ||= payload.text.trim().length > 0
    }
    if (event === 'done') {
      if (!visibleAnswer) throw new Error('AI가 빈 답변을 반환했습니다.')
      terminal = 'done'
      donePayload = payload
      return
    }
    if (event === 'error') terminal = 'error'
    onEvent(event, payload)
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      frames.forEach(frameEvent)
      if (done) {
        if (buffer.trim()) frameEvent(buffer)
        if (!terminal) throw new Error('AI 응답이 완료되기 전에 연결이 끊겼습니다.')
        if (terminal === 'done') onEvent('done', donePayload)
        return terminal
      }
    }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
