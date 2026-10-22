import assert from 'node:assert/strict'
import test from 'node:test'
import { mergeMessages, mergeRoomHistory } from './messages.js'

test('history and live deliveries merge once in server message order', () => {
  const live = [{ messageNo: 3, content: 'live' }, { messageNo: 2, content: 'overlap' }]
  const history = [{ messageNo: 1, content: 'offline' }, { messageNo: 2, content: 'overlap' }]
  assert.deepEqual(mergeMessages(live, history).map((message) => message.messageNo), [1, 2, 3])
  assert.equal(live.length, 2)
  assert.equal(history.length, 2)
})

test('offline read state recovers and stale history cannot undo a live read receipt', () => {
  const unread = [{ messageNo: 1, readAt: null }]
  const read = [{ messageNo: 1, readAt: '2026-10-06 13:00:00' }]
  assert.deepEqual(mergeMessages(unread, read), read)
  assert.deepEqual(mergeMessages(read, unread), read)
})

test('late history from a previously selected room leaves the current room intact', () => {
  const current = [{ messageNo: 2, roomNo: 20, content: '현재 대화' }]
  const stale = [{ messageNo: 1, roomNo: 10, content: '이전 대화' }]
  assert.strictEqual(mergeRoomHistory(current, stale, 10, 20), current)
  assert.equal(mergeRoomHistory([], stale, 10, 10)[0].content, '이전 대화')
})
