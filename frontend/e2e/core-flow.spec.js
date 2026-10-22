import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { test, expect } from '@playwright/test'

const suffix = process.env.ERP_E2E_USER_SUFFIX
const password = 'browser-test-password'
const users = {
  drafter: { id: `e2e-${suffix}-draft`, name: `E2E사원${suffix}` },
  first: { id: `e2e-${suffix}-first`, name: `E2E대리${suffix}` },
  second: { id: `e2e-${suffix}-second`, name: `E2E팀장${suffix}` },
  password: { id: `e2e-${suffix}-password`, name: `E2E암호${suffix}` },
  admin: { id: `e2e-${suffix}-admin`, name: `E2E관리자${suffix}` },
  formA: { id: `e2e-${suffix}-form-a`, name: `E2E수정A${suffix}` },
  formB: { id: `e2e-${suffix}-form-b`, name: `E2E수정B${suffix}` }
}

async function apiLogin(context, user, userPassword = password) {
  const session = await (await context.request.get('/ERP/api/session')).json()
  const response = await context.request.post('/ERP/login/login.do', {
    form: { usernum: user.id, userpw: userPassword, [session.csrf.parameterName]: session.csrf.token }
  })
  expect(response.status()).toBe(200)
  expect(await response.text()).toBe('OK')
}

async function openWorkspace(page, path) {
  // These requests start on every full React shell mount, before the timer is armed.
  const initialData = ['dashboard', 'chat/rooms', 'chat/contacts'].map((endpoint) =>
    page.waitForResponse((response) => new URL(response.url()).pathname === `/ERP/api/${endpoint}` && response.ok()))
  await page.goto(path)
  await Promise.all(initialData)
}

async function expireSession(page, anonymousRequest) {
  const response = await page.request.get('/ERP/api/session?e2eTimeout=arm')
  expect(response.ok()).toBeTruthy()
  const { probe } = await response.json()
  // Poll without the browser's cookies; checking a protected request early would renew it.
  await expect.poll(async () => {
    const status = await anonymousRequest.get(`/ERP/api/session?e2eTimeout=idle&probe=${probe}`)
    expect(status.ok()).toBeTruthy()
    return (await status.json()).idleTimeoutReached
  }, { timeout: 10000, intervals: [100, 200] }).toBe(true)
}

async function expectLoginAfterExpiry(page) {
  await expect(page).toHaveURL(/\/ERP\/login\/login\.do$/)
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
  expect((await (await page.request.get('/ERP/api/session')).json()).authenticated).toBe(false)
}

async function createPendingDraft(context, title) {
  const { csrf } = await (await context.request.get('/ERP/api/session')).json()
  const response = await context.request.post('/ERP/approval/write.do', {
    multipart: { kind: '기안서', approval_title: title, approval_content: '세션 만료 시 승인되지 않아야 합니다.',
      approval_target: users.first.id, [csrf.parameterName]: csrf.token },
    maxRedirects: 0
  })
  expect(response.status()).toBe(302)
  return response.headers().location
}

test('UI login, ordered approval and attachment download preserve the document and bytes', async ({ page }) => {
  const title = `브라우저 결재 ${randomUUID()}`
  const bytes = Buffer.concat([Buffer.from(`ERP attachment ${title}\n`), Buffer.from([0, 1, 127, 255])])
  await page.goto('/ERP/login/login.do')
  await page.getByLabel('사원번호', { exact: true }).fill(users.drafter.id)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  const initialData = ['dashboard', 'chat/rooms', 'chat/contacts'].map((endpoint) =>
    page.waitForResponse((response) => new URL(response.url()).pathname === `/ERP/api/${endpoint}` && response.ok()))
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await Promise.all(initialData)
  await expect(page).toHaveURL(/\/ERP\/main\.do$/)
  await page.getByRole('link', { name: '결재 작성', exact: true }).click()
  await page.getByRole('combobox', { name: '문서 구분', exact: true }).selectOption('기안서')
  await page.getByLabel('제목', { exact: true }).fill(title)
  await page.getByLabel('내용', { exact: true }).fill('두 결재자가 선택한 순서대로 승인합니다.')
  await page.getByRole('checkbox', { name: new RegExp(users.first.name) }).check()
  await page.getByRole('checkbox', { name: new RegExp(users.second.name) }).check()
  await page.getByLabel('첨부파일', { exact: true }).setInputFiles({ name: 'approval-e2e.bin', mimeType: 'application/octet-stream', buffer: bytes })
  await page.getByRole('button', { name: '등록 완료', exact: true }).click()
  await expect(page).toHaveURL(/\/approval\/view\.do\?approval_no=\d+$/)
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  const path = new URL(page.url()).pathname + new URL(page.url()).search
  const id = new URL(page.url()).searchParams.get('approval_no')
  const detail = await (await page.request.get(`/ERP/api/approvals/${id}`)).json()
  expect(detail.lines.map((line) => line.target)).toEqual([users.first.id, users.second.id])

  const downloadEvent = page.waitForEvent('download')
  await page.getByRole('link', { name: 'approval-e2e.bin', exact: true }).click()
  const download = await downloadEvent
  expect(download.suggestedFilename()).toBe('approval-e2e.bin')
  expect(await readFile(await download.path())).toEqual(bytes)

  await apiLogin(page.context(), users.second)
  await openWorkspace(page, path)
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '승인', exact: true })).toHaveCount(0)

  await apiLogin(page.context(), users.first)
  await openWorkspace(page, '/ERP/approval/recv.do')
  await page.getByRole('link', { name: new RegExp(title) }).click()
  await page.getByLabel('결재 의견', { exact: true }).fill('1차 검토 완료')
  await page.getByRole('button', { name: '승인', exact: true }).click()
  await expect(page.getByText('진행중', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '승인', exact: true })).toHaveCount(0)
  const firstDashboard = await (await page.request.get('/ERP/api/dashboard')).json()
  await expect(page.getByRole('navigation', { name: '주요 메뉴' }).getByRole('link', { name: /결재수신/ }).locator('b')).toHaveText(String(firstDashboard.counts['수신']))

  await apiLogin(page.context(), users.second)
  await openWorkspace(page, path)
  await page.getByLabel('결재 의견', { exact: true }).fill('최종 검토 완료')
  await page.getByRole('button', { name: '승인', exact: true }).click()
  await expect(page.getByRole('button', { name: '승인', exact: true })).toHaveCount(0)
  const approved = await (await page.request.get(`/ERP/api/approvals/${id}`)).json()
  expect(approved.item.status).toBe('승인')
  expect(approved.lines.map((line) => line.status)).toEqual(['승인', '승인'])
  expect(approved.lines.map((line) => line.comment)).toEqual(['1차 검토 완료', '최종 검토 완료'])
  await apiLogin(page.context(), users.drafter)
  await openWorkspace(page, path)
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
  await expect(page.getByText('승인', { exact: true }).first()).toBeVisible()
})

test('native idle session expiry redirects an in-app read to login', async ({ page, request }) => {
  await apiLogin(page.context(), users.drafter)
  await openWorkspace(page, '/ERP/approval/list.do?mode=0')
  await expect(page.getByRole('heading', { name: '내 결재 관리', exact: true })).toBeVisible()
  await expireSession(page, request)
  await page.getByRole('navigation', { name: '주요 메뉴' }).getByRole('link', { name: '공지사항', exact: true }).click()
  await expectLoginAfterExpiry(page)
})

test('native idle session expiry redirects a JSON write and leaves the password unchanged', async ({ page, request }) => {
  await apiLogin(page.context(), users.drafter)
  await openWorkspace(page, '/ERP/login/changepw.do')
  await page.getByLabel('현재 비밀번호', { exact: true }).fill(password)
  await page.getByLabel('새 비밀번호', { exact: true }).fill('must-not-be-saved')
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('must-not-be-saved')
  await expireSession(page, request)
  await page.getByRole('button', { name: '비밀번호 변경', exact: true }).click()
  await expectLoginAfterExpiry(page)
  await apiLogin(page.context(), users.drafter)
})

test('native idle session expiry redirects an approval POST and keeps it pending', async ({ page, request }) => {
  await apiLogin(page.context(), users.drafter)
  const path = await createPendingDraft(page.context(), `만료 결재 ${randomUUID()}`)
  const id = new URL(path, process.env.ERP_E2E_BASE_URL).searchParams.get('approval_no')
  await apiLogin(page.context(), users.first)
  await openWorkspace(page, path)
  await page.getByLabel('결재 의견', { exact: true }).fill('만료 후 저장되면 안 됩니다.')
  await expireSession(page, request)
  await page.getByRole('button', { name: '승인', exact: true }).click()
  await expectLoginAfterExpiry(page)
  await apiLogin(page.context(), users.drafter)
  const detail = await (await page.request.get(`/ERP/api/approvals/${id}`)).json()
  expect(detail.item.status).toBe('대기중')
  expect(detail.lines[0].status).toBe('대기')
  expect(detail.lines[0].comment).toBeFalsy()
})

test('native idle session expiry redirects multipart submission and does not create a document', async ({ page, request }) => {
  const title = `만료 작성 ${randomUUID()}`
  await apiLogin(page.context(), users.drafter)
  await openWorkspace(page, '/ERP/approval/write.do')
  await page.getByRole('combobox', { name: '문서 구분', exact: true }).selectOption('기안서')
  await page.getByLabel('제목', { exact: true }).fill(title)
  await page.getByLabel('내용', { exact: true }).fill('만료 후 생성되면 안 됩니다.')
  await page.getByRole('checkbox', { name: new RegExp(users.first.name) }).check()
  await expireSession(page, request)
  await page.getByRole('button', { name: '등록 완료', exact: true }).click()
  await expectLoginAfterExpiry(page)
  await apiLogin(page.context(), users.drafter)
  const drafts = await (await page.request.get(`/ERP/api/approvals?keyword=${encodeURIComponent(title)}`)).json()
  expect(drafts.totalCount).toBe(0)
})

test('successful password change clears the form and accepts only the new password', async ({ page, browser }) => {
  const newPassword = `changed-${randomUUID()}`
  const verification = await browser.newContext()
  let changed = false
  try {
    await page.goto('/ERP/login/login.do')
    await page.getByLabel('사원번호', { exact: true }).fill(users.password.id)
    await page.getByLabel('비밀번호', { exact: true }).fill(password)
    await page.context().clearCookies()
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('로그인 보안 정보가 갱신되었습니다. 다시 로그인해 주세요.')
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page).toHaveURL(/\/ERP\/main\.do$/)
    await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible()
    await openWorkspace(page, '/ERP/login/changepw.do')
    await page.getByLabel('현재 비밀번호', { exact: true }).fill(password)
    await page.getByLabel('새 비밀번호', { exact: true }).fill(newPassword)
    await page.getByLabel('새 비밀번호 확인', { exact: true }).fill(newPassword)
    await page.getByRole('button', { name: '비밀번호 변경', exact: true }).click()
    await expect(page.getByRole('status')).toHaveText('비밀번호가 변경되었습니다.')
    changed = true
    await expect(page.getByLabel('현재 비밀번호', { exact: true })).toHaveValue('')
    await expect(page.getByLabel('새 비밀번호', { exact: true })).toHaveValue('')
    await expect(page.getByLabel('새 비밀번호 확인', { exact: true })).toHaveValue('')
    await expect(page.getByRole('alert')).toHaveCount(0)
    const { csrf } = await (await verification.request.get('/ERP/api/session')).json()
    const oldLogin = await verification.request.post('/ERP/login/login.do', {
      form: { usernum: users.password.id, userpw: password, [csrf.parameterName]: csrf.token }
    })
    expect(oldLogin.status()).toBe(401)
    await apiLogin(verification, users.password, newPassword)
    expect((await (await verification.request.get('/ERP/api/users/me')).json()).usernum).toBe(users.password.id)
  } finally {
    if (changed) {
      const { csrf } = await (await page.request.get('/ERP/api/session')).json()
      const restored = await page.request.post('/ERP/api/users/me/password', {
        headers: { [csrf.headerName]: csrf.token },
        data: { currentPassword: newPassword, newPassword: password, confirmPassword: password }
      })
      expect(restored.ok()).toBeTruthy()
    }
    await verification.close()
  }
})

test('messenger marks messages read only in the visible selected conversation', async ({ page, browser }) => {
  const receivingContext = await browser.newContext()
  const receiver = await receivingContext.newPage()
  try {
    await apiLogin(page.context(), users.drafter)
    await apiLogin(receivingContext, users.first)
    await openWorkspace(page, '/ERP/main.do')
    await openWorkspace(receiver, '/ERP/main.do')
    for (const [peer, partner] of [[page, users.first], [receiver, users.drafter]]) {
      await peer.getByRole('button', { name: '사내 메신저 열기', exact: true }).click()
      await peer.getByRole('combobox', { name: '대화 상대', exact: true }).selectOption(partner.id)
      await peer.getByRole('button', { name: '대화 시작', exact: true }).click()
      await expect(peer.getByLabel('사내 메신저', { exact: true }).getByText('연결됨', { exact: true })).toBeVisible()
      await expect(peer.getByRole('textbox', { name: '메시지', exact: true })).toBeVisible()
    }
    const rooms = await (await page.request.get('/ERP/api/chat/rooms')).json()
    const roomNo = rooms.find((room) => room.partnerUsernum === users.first.id).roomNo
    const messageState = async (content) => {
      const history = await (await page.request.get(`/ERP/api/chat/rooms/${roomNo}/messages`)).json()
      return history.find((message) => message.content === content)
    }
    let readRequests = 0
    receiver.on('request', (request) => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === `/ERP/api/chat/rooms/${roomNo}/read`) readRequests++
    })
    const send = async () => {
      const content = `읽음 회귀 ${randomUUID()}`
      const readsBeforeDelivery = readRequests
      let receivedRooms
      const delivered = receiver.waitForResponse(async (response) => {
        if (new URL(response.url()).pathname !== '/ERP/api/chat/rooms' || response.request().method() !== 'GET' || !response.ok()) return false
        const data = await response.json()
        if (data.find((room) => room.roomNo === roomNo)?.lastMessage !== content) return false
        receivedRooms = data
        return true
      })
      await page.getByRole('textbox', { name: '메시지', exact: true }).fill(content)
      await page.getByRole('button', { name: '메시지 보내기', exact: true }).click()
      await expect(page.getByText(content, { exact: true })).toBeVisible()
      await (await delivered).finished()
      await receiver.bringToFront()
      expect(receivedRooms.find((room) => room.roomNo === roomNo).unreadCount).toBeGreaterThan(0)
      await expect(receiver.getByRole('button', { name: /^사내 메신저 (열기|닫기)$/ }).locator('b')).toHaveText(String(receivedRooms.reduce((sum, room) => sum + room.unreadCount, 0)))
      // Complete the receiver callback's room refresh and rendered UI before checking forbidden reads.
      await receiver.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
      expect(readRequests).toBe(readsBeforeDelivery)
      expect((await messageState(content)).readAt).toBeNull()
      return content
    }
    const openAndRead = async (content) => {
      await receiver.bringToFront()
      await receiver.getByRole('button', { name: '사내 메신저 열기', exact: true }).click()
      await expect(receiver.getByText(content, { exact: true })).toBeVisible()
      await expect.poll(async () => (await messageState(content)).readAt).not.toBeNull()
      await expect.poll(async () => (await (await receiver.request.get('/ERP/api/chat/rooms')).json()).find((room) => room.roomNo === roomNo).unreadCount).toBe(0)
    }

    await receiver.getByRole('button', { name: '메신저 닫기', exact: true }).click()
    const closedMessage = await send()
    await expect(receiver.getByLabel('사내 메신저', { exact: true })).toHaveCount(0)
    const readPath = `**/ERP/api/chat/rooms/${roomNo}/read?*`
    await receiver.route(readPath, (route) => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '읽음 재시도 확인' }) }))
    await receiver.bringToFront()
    await receiver.getByRole('button', { name: '사내 메신저 열기', exact: true }).click()
    await expect(receiver.getByText(closedMessage, { exact: true })).toBeVisible()
    await expect(receiver.getByRole('alert')).toHaveText('읽음 재시도 확인')
    expect((await messageState(closedMessage)).readAt).toBeNull()
    await receiver.getByRole('button', { name: '메신저 닫기', exact: true }).click()
    await receiver.unroute(readPath)
    await openAndRead(closedMessage)
    await expect(receiver.getByRole('alert')).toHaveCount(0)
    await receiver.getByRole('button', { name: 'AI 챗봇 열기', exact: true }).click()
    const duringAi = await send()
    await expect(receiver.getByLabel('사내 메신저', { exact: true })).toHaveCount(0)
    await openAndRead(duringAi)

    await receiver.getByRole('button', { name: '대화방 목록', exact: true }).click()
    await receiver.getByRole('combobox', { name: '대화 상대', exact: true }).selectOption(users.second.id)
    await receiver.getByRole('button', { name: '대화 시작', exact: true }).click()
    await expect(receiver.getByLabel('사내 메신저', { exact: true }).getByText(users.second.name, { exact: true })).toBeVisible()
    const inOtherRoom = await send()
    await expect(receiver.getByText(inOtherRoom, { exact: true })).toHaveCount(0)
    await expect(receiver.getByLabel('사내 메신저', { exact: true }).getByText(users.second.name, { exact: true })).toBeVisible()
    await receiver.bringToFront()
    await receiver.getByRole('button', { name: '대화방 목록', exact: true }).click()
    await receiver.getByRole('button', { name: new RegExp(users.drafter.name) }).click()
    await expect(receiver.getByText(inOtherRoom, { exact: true })).toBeVisible()
    await expect.poll(async () => (await messageState(inOtherRoom)).readAt).not.toBeNull()
  } finally {
    await receivingContext.close()
  }
})

async function historyNavigate(page, path) {
  await page.evaluate((next) => {
    window.history.pushState({}, '', next)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

test('late approval detail and history form changes keep the displayed and submitted document aligned', async ({ page }) => {
  await apiLogin(page.context(), users.drafter)
  const firstTitle = `대상 A ${randomUUID()}`
  const secondTitle = `대상 B ${randomUUID()}`
  const firstPath = await createPendingDraft(page.context(), firstTitle)
  const secondPath = await createPendingDraft(page.context(), secondTitle)
  const firstId = new URL(firstPath, process.env.ERP_E2E_BASE_URL).searchParams.get('approval_no')
  const secondId = new URL(secondPath, process.env.ERP_E2E_BASE_URL).searchParams.get('approval_no')
  await openWorkspace(page, '/ERP/approval/list.do?mode=0')
  let releaseFirst
  let firstStarted
  let firstFinished
  const started = new Promise((resolve) => { firstStarted = resolve })
  const released = new Promise((resolve) => { releaseFirst = resolve })
  const finished = new Promise((resolve) => { firstFinished = resolve })
  await page.route(`**/ERP/api/approvals/${firstId}`, async (route) => {
    const response = await route.fetch()
    firstStarted()
    await released
    await route.fulfill({ response })
    firstFinished()
  })
  try {
    await historyNavigate(page, firstPath)
    await started
    await historyNavigate(page, secondPath)
    await expect(page.getByRole('heading', { name: secondTitle, exact: true })).toBeVisible()
    releaseFirst()
    await finished
    await expect(page.getByRole('heading', { name: secondTitle, exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: firstTitle, exact: true })).toHaveCount(0)
  } finally {
    releaseFirst()
    await page.unroute(`**/ERP/api/approvals/${firstId}`)
  }

  await historyNavigate(page, `/ERP/approval/modify.do?approval_no=${firstId}`)
  await expect(page.getByLabel('제목', { exact: true })).toHaveValue(firstTitle)
  await page.getByLabel('제목', { exact: true }).fill('A에서 작성 중인 입력')
  await historyNavigate(page, `/ERP/approval/modify.do?approval_no=${secondId}`)
  await expect(page.getByLabel('제목', { exact: true })).toHaveValue(secondTitle)
  await expect(page.locator('input[name="approval_no"]')).toHaveValue(secondId)
  await page.getByRole('button', { name: '수정 완료', exact: true }).click()
  await expect(page.getByRole('heading', { name: secondTitle, exact: true })).toBeVisible()
  expect((await (await page.request.get(`/ERP/api/approvals/${firstId}`)).json()).item.title).toBe(firstTitle)
  expect((await (await page.request.get(`/ERP/api/approvals/${secondId}`)).json()).item.title).toBe(secondTitle)
})

test('employee form history changes reset values before updating the new target', async ({ page }) => {
  await apiLogin(page.context(), users.admin)
  await openWorkspace(page, `/ERP/user/modify.do?usernum=${users.formA.id}`)
  await expect(page.getByLabel('사원명', { exact: true })).toHaveValue(users.formA.name)
  await page.getByLabel('사원명', { exact: true }).fill('A의 저장하지 않은 이름')
  await historyNavigate(page, `/ERP/user/modify.do?usernum=${users.formB.id}`)
  await expect(page.getByLabel('사원명', { exact: true })).toHaveValue(users.formB.name)
  await expect(page.getByLabel('사원번호', { exact: true })).toHaveValue(users.formB.id)
  await page.getByRole('button', { name: '수정 완료', exact: true }).click()
  await expect(page.getByRole('heading', { name: users.formB.name, exact: true, level: 1 })).toBeVisible()
  expect((await (await page.request.get(`/ERP/api/users/${users.formA.id}`)).json()).name).toBe(users.formA.name)
  expect((await (await page.request.get(`/ERP/api/users/${users.formB.id}`)).json()).name).toBe(users.formB.name)
})
