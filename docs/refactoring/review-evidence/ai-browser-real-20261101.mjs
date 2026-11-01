import assert from 'node:assert/strict'
import { readFile, writeFile, mkdir } from 'node:fs/promises'

const project = new URL('../../../', import.meta.url).pathname
const { chromium } = await import(`${project}frontend/node_modules/playwright-core/index.mjs`)
const environment = JSON.parse(await readFile(`${project}.local-dev/portfolio-demo-20261101/environment.json`, 'utf8'))
const baseURL = `http://127.0.0.1:${environment.app_port}`
const output = `${project}docs/refactoring/review-evidence/ai-browser-real-20261101.json`
const screenshot = `${project}docs/refactoring/screenshots/portfolio-20261101/ai-answer-real-model.png`
const browser = await chromium.launch()
const page = await browser.newPage({ baseURL, viewport: { width: 1440, height: 960 } })
const errors = []
page.on('pageerror', (error) => errors.push(String(error)))
await page.addInitScript(() => {
  const originalFetch = window.fetch.bind(window)
  window.aiVerificationResponses = []
  window.fetch = async (...args) => {
    const response = await originalFetch(...args)
    if (new URL(response.url).pathname === '/ERP/api/ai/chat') {
      const captured = { status: response.status, contentType: response.headers.get('content-type') }
      captured.completed = response.clone().text().then((rawSse) => { captured.rawSse = rawSse })
      window.aiVerificationResponses.push(captured)
    }
    return response
  }
})
const results = []
try {
  await page.goto('/ERP/login/login.do')
  await page.getByLabel('사원번호', { exact: true }).fill('2005002')
  await page.getByLabel('비밀번호', { exact: true }).fill('1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.waitForURL('**/ERP/main.do')
  await page.getByRole('button', { name: 'AI 챗봇 열기', exact: true }).click()
  const panel = page.getByLabel('AI 업무 도우미', { exact: true })
  const input = panel.getByRole('textbox', { name: '챗봇 메시지', exact: true })
  const cases = [
    { question: '공지 "ERP 개발 환경 안내" 요약해줘', fact: /개발\s*환경.*준비/s, source: /공지\s*\d+\s*번/, model: /qwen3\.5:2b/ },
    { question: '결재 "Spring Boot 전환 검토" 알려줘', fact: /기존\s*ERP/i, source: /결재\s*\d+\s*번/, model: /qwen3\.5:2b/ },
    { question: '공지 999999 내용 알려줘', fact: /없습니다/, source: /열람 가능한 공지/, model: /context-only/ },
    { question: '서울 날씨 알려줘', fact: /공지와 현재 내 순서인 결재/, source: /입력해 주세요/, model: /context-only/ },
    { question: '공지 "ERP 개발 환경 안내" 3줄로 요약해줘', fact: /개발\s*환경.*준비/s, source: /공지\s*\d+\s*번/, model: /qwen3\.5:2b/ }
  ]
  for (let index = 0; index < cases.length; index++) {
    const item = cases[index]
    await input.fill(item.question)
    await panel.getByRole('button', { name: '메시지 보내기', exact: true }).click()
    const answer = panel.locator('.bot-message').last()
    await answer.locator('small').waitFor({ state: 'visible', timeout: 70000 })
    assert.equal(await input.isEnabled(), true)
    assert.equal(await panel.getByRole('alert').count(), 0)
    const text = await answer.locator('span').innerText()
    const metrics = await answer.locator('small').innerText()
    assert.match(text.split('\n\n[조회 자료]')[0], item.fact)
    assert.match(text, item.source)
    assert.match(metrics, item.model)
    const transport = await page.evaluate(async (number) => {
      const captured = window.aiVerificationResponses[number]
      await captured.completed
      return { status: captured.status, contentType: captured.contentType, rawSse: captured.rawSse }
    }, index)
    assert.equal(transport.status, 200)
    assert.match(transport.contentType, /text\/event-stream/)
    assert.match(transport.rawSse, /event:done/)
    assert.doesNotMatch(transport.rawSse, /event:error/)
    results.push({ question: item.question, answer: text, metrics, ...transport })
    if (index === 1) await page.screenshot({ path: screenshot, fullPage: true })
  }
  assert.deepEqual(errors, [])
  await mkdir(new URL('.', `file://${output}`).pathname, { recursive: true })
  await writeFile(output, JSON.stringify({ verifiedAt: new Date().toISOString(), scope: 'Real native WAR + dedicated MySQL + Ollama qwen3.5:2b + Chromium UI. Synthetic seed data only; no .local-dev/source mutation.', browser: browser.version(), warSha256: environment.war_sha256, passed: results.length, pageErrors: errors, results }, null, 2) + '\n')
  console.log(JSON.stringify({ passed: results.length, output, screenshot }))
} finally {
  await browser.close()
}
