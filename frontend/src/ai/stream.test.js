import test from 'node:test'
import assert from 'node:assert/strict'
import { consumeAnswer } from './stream.js'

function streamResponse(chunks, type = 'text/event-stream') {
  return new Response(new ReadableStream({
    start(controller) {
      for (const text of chunks) controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    }
  }), { headers: { 'content-type': type } })
}

const token = 'event:token\ndata:{"text":"근거 답변"}\n\n'
const done = 'event:done\ndata:{"model":"test-model","durationMs":10}\n\n'

test('split SSE frames stream tokens and finish only with a visible answer and done', async () => {
  const events = []
  const body = token + done
  assert.equal(await consumeAnswer(streamResponse([body.slice(0, 16), body.slice(16)]), (name, payload) => events.push({ name, payload })), 'done')
  assert.deepEqual(events.map((event) => event.name), ['token', 'done'])
  assert.equal(events[0].payload.text, '근거 답변')
})

test('HTML, incomplete EOF and empty done never publish success metrics', async () => {
  for (const response of [streamResponse(['login'], 'text/html'), streamResponse([token]), streamResponse([done]),
    streamResponse(['event:token\ndata:{"text":"   "}\n\n' + done])]) {
    const events = []
    await assert.rejects(() => consumeAnswer(response, (name) => events.push(name)))
    assert.equal(events.includes('done'), false)
  }
})

test('server error is delivered once without turning into a second EOF error', async () => {
  const events = []
  assert.equal(await consumeAnswer(streamResponse(['event:error\ndata:{"message":"모델 오류"}\n\n']), (name, payload) => events.push({ name, payload })), 'error')
  assert.deepEqual(events, [{ name: 'error', payload: { message: '모델 오류' } }])
})

test('malformed data after done is still rejected', async () => {
  const events = []
  await assert.rejects(() => consumeAnswer(streamResponse([token + done + 'event:token\ndata:{"text":"추가"}\n\n']), (name) => events.push(name)))
  assert.equal(events.includes('done'), false)
})
