import { chromium, expect } from '@playwright/test'
import { readFile, appendFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const root = resolve(process.env.ERP_PERF_ROOT || '')
if (!root.split('/').at(-1).startsWith('performance-') || !root.includes('/.local-dev/')) throw new Error('An owned performance root is required')
const output = resolve(process.env.ERP_PERF_OUTPUT)
const state = JSON.parse(await readFile(`${root}/environment.json`))
const fixture = JSON.parse(await readFile(`${root}/performance-fixture.json`))
if (state.root !== root) throw new Error('Environment identity differs')
const baseURL = `http://127.0.0.1:${state.app_port}`
const repeats = Number(process.env.ERP_PERF_REPEATS)
const samples = Number(process.env.ERP_PERF_BROWSER_SAMPLES)
const selectedRepeat = process.env.ERP_PERF_BROWSER_REPEAT ? Number(process.env.ERP_PERF_BROWSER_REPEAT) : null
const browser = await chromium.launch({ headless: true })
let failed = 0

function observe() {
  const supported = PerformanceObserver.supportedEntryTypes
  const entries = {}
  const observers = []
  let firstInput = null
  const stopLcp = () => { firstInput ??= performance.now() }
  addEventListener('pointerdown', stopLcp, { once: true, capture: true })
  addEventListener('keydown', stopLcp, { once: true, capture: true })
  for (const type of ['navigation', 'resource', 'largest-contentful-paint', 'layout-shift', 'event', 'longtask']) {
    entries[type] = supported.includes(type) ? [] : null
    if (!supported.includes(type)) continue
    const save = list => entries[type].push(...list.map(value => value.toJSON()))
    const observer = new PerformanceObserver(list => save(list.getEntries()))
    observer.observe({ type, buffered: true, ...(type === 'event' ? { durationThreshold: 16 } : {}) })
    observers.push({ observer, save })
  }
  window.perfFinish = async () => {
    const domReady = performance.now()
    const frameCallback = await new Promise(requestAnimationFrame)
    const end = performance.now()
    for (const { observer, save } of observers) { save(observer.takeRecords()); observer.disconnect() }
    const shifts = (entries['layout-shift'] || []).filter(value => !value.hadRecentInput)
    let cls = 0, windowStart = 0, last = 0, sum = 0
    for (const shift of shifts) {
      if (!windowStart || shift.startTime - last >= 1000 || shift.startTime - windowStart > 5000) { windowStart = shift.startTime; sum = 0 }
      sum += shift.value; last = shift.startTime; cls = Math.max(cls, sum)
    }
    const lcps = (entries['largest-contentful-paint'] || []).filter(value => firstInput === null || value.startTime < firstInput)
    return { dom_ready_ms: domReady, frame_callback_ms: frameCallback, observation_end_ms: end, first_input_ms: firstInput,
      lcp_ms: lcps.at(-1)?.startTime ?? null, lcp_absence: lcps.length ? null : 'unsupported or no candidate observed before input',
      cls: supported.includes('layout-shift') ? cls : null, entries, supported,
      event_note: 'Synthetic observed events with 16ms threshold; not full-visit or field INP',
      paint_note: 'DOM and rAF callback timestamps do not prove paint completion' }
  }
}

async function login(context) {
  const response = await context.request.get('/ERP/api/session')
  const { csrf } = await response.json()
  const loggedIn = await context.request.post('/ERP/login/login.do', { form: { usernum: 'perf-u000', userpw: 'performance-local-only', [csrf.parameterName]: csrf.token } })
  expect(loggedIn.status()).toBe(200)
  expect(await loggedIn.text()).toBe('OK')
}

async function jsonResponse(page, path) {
  return page.waitForResponse(response => new URL(response.url()).pathname === path && response.ok(), { timeout: 15000 }).then(response => response.json())
}

const cases = [
  { name: 'home', path: '/ERP/main.do', api: '/ERP/api/dashboard', async ready(page, body) { for (const notice of body.notices) await expect(page.getByText(notice.title, { exact: true }).first()).toBeVisible() } },
  { name: 'notices', path: '/ERP/notice/list.do?searchWord=PERF', api: '/ERP/api/notices', async ready(page, body) {
    expect(body.totalCount).toBe(fixture.size)
    for (const notice of body.items) await expect(page.getByText(notice.title, { exact: true })).toBeVisible()
  } },
  { name: 'notice-search', path: '/ERP/notice/list.do?searchWord=PERF', api: '/ERP/api/notices', async ready(page, body) {
    expect(body.totalCount).toBe(fixture.size)
    for (const notice of body.items) await expect(page.getByText(notice.title, { exact: true })).toBeVisible()
    await page.getByPlaceholder('공지 제목 검색', { exact: true }).fill('rare')
    const response = jsonResponse(page, '/ERP/api/notices')
    const button = page.getByRole('button', { name: '검색', exact: true })
    await button.evaluate(element => element.addEventListener('click', () => { window.perfSearchClick = performance.now() }, { once: true, capture: true }))
    const clickRequest = await page.evaluate(() => performance.now())
    await button.click()
    const filtered = await response
    expect(filtered.totalCount).toBe(fixture.size / 100)
    for (const notice of filtered.items) { expect(notice.title).toContain('rare'); await expect(page.getByText(notice.title, { exact: true })).toBeVisible() }
    const timing = await page.evaluate(() => ({ click_event_ms: window.perfSearchClick, result_dom_ms: performance.now() }))
    return { ...timing, automation_click_request_ms: clickRequest, action: 'search', expected_count: fixture.size / 100, actual_count: filtered.totalCount }
  } },
  { name: 'completed', path: '/ERP/approval/allok.do?keyword=PERF&team=%EA%B0%9C%EB%B0%9C', api: '/ERP/api/approvals/completed', async ready(page, body) {
    expect(body.items.length).toBe(10)
    for (const item of body.items) { expect(item.drafter.team).toBe('개발'); await expect(page.getByText(item.title, { exact: true })).toBeVisible() }
  } },
  { name: 'detail', path: '/ERP/approval/view.do?approval_no=1000000', api: '/ERP/api/approvals/1000000', async ready(page, body) {
    expect(body.item.approvalNo).toBe(1000000)
    expect(body.lines.length).toBe(3)
    await expect(page.getByRole('heading', { name: body.item.title, exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'attachment-0.bin', exact: true })).toBeVisible()
  } },
  { name: 'history', path: '/ERP/main.do', api: '/ERP/api/chat/rooms', async ready(page, rooms) {
    const room = rooms.find(value => value.roomNo === 1000000)
    expect(room.partnerUsernum).toBe('perf-u001')
    await page.getByRole('button', { name: '사내 메신저 열기', exact: true }).click()
    await expect(page.getByText('연결됨', { exact: true })).toBeVisible()
    const history = jsonResponse(page, '/ERP/api/chat/rooms/1000000/messages')
    await page.locator('.messenger-room-list button').filter({ has: page.getByText(room.partnerName, { exact: true }) }).click()
    const body = await history
    expect(body.length).toBeGreaterThan(0)
    await expect(page.locator('.messenger-message')).toHaveCount(body.length)
    await expect(page.getByText(body.at(-1).content, { exact: true })).toBeVisible()
  } }
]

try {
  await writeFile(`${output}/browser-environment.json`, JSON.stringify({ chromium: browser.version(), node: process.version, playwright: JSON.parse(await readFile(new URL('../node_modules/@playwright/test/package.json', import.meta.url))).version, viewport: { width: 1440, height: 900 }, headless: true, workers: 1, retries: 0, observation_window: 'navigation start through fixture-backed DOM readiness and next rAF; duration varies by case', browser_cache: 'cold=new context after API login; warm=same context second navigation', os_db_cache: 'not cleared', read_state: 'seed read flags restored before each repeat; history warmup marks visible history read before measured history visits', samples_per_case_per_cache_per_repeat: samples, planned_samples: repeats * cases.length * 2 * samples, warmups: 1, cases: cases.map(value => value.name) }, null, 2))
  for (const repeat of selectedRepeat ? [selectedRepeat] : Array.from({ length: repeats }, (_, i) => i + 1)) {
    for (const scenario of cases) {
      for (let sequence = -1; sequence < samples; sequence++) {
        const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 900 } })
        await login(context)
        await context.addInitScript(observe)
        const page = await context.newPage()
        for (const cache of ['cold', 'warm']) {
          const row = { repeat, case: `${scenario.name}-${cache}`, sequence, warmup: sequence < 0, ok: false }
          const start = performance.now()
          const requests = []
          const capture = response => { if (response.url().includes('/ERP/api/')) requests.push({ path: new URL(response.url()).pathname, status: response.status() }) }
          page.on('response', capture)
          try {
            const body = jsonResponse(page, scenario.api)
            body.catch(() => {})
            await page.goto(scenario.path, { waitUntil: 'domcontentloaded', timeout: 15000 })
            row.action = await scenario.ready(page, await body) ?? null
            if (row.action) {
              row.action.elapsed_ms = row.action.result_dom_ms - row.action.click_event_ms
              row.action.automation_to_dom_ms = row.action.result_dom_ms - row.action.automation_click_request_ms
            }
            row.elapsed_ms = performance.now() - start
            row.observation = await page.evaluate(() => window.perfFinish())
            row.dom_elements = await page.locator('*').count()
            row.api_requests = requests
            row.ok = true
          } catch (error) {
            row.elapsed_ms = performance.now() - start
            row.error = error.message.slice(0, 300)
            row.timeout = error.name === 'TimeoutError'
            if (sequence >= 0) failed++
          } finally {
            page.off('response', capture)
            await appendFile(`${output}/browser.jsonl`, `${JSON.stringify(row)}\n`)
          }
        }
        await context.close()
      }
    }
  }
} finally { await browser.close() }
if (failed) throw new Error(`${failed} browser samples failed; raw failures retained`)
