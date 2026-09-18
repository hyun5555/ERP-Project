import { useEffect, useMemo, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

const BASE = '/ERP'
const APP_PATHS = new Set([
  '/ERP/main.do', '/ERP/approval/list.do', '/ERP/approval/recv.do',
  '/ERP/approval/write.do', '/ERP/approval/modify.do', '/ERP/approval/view.do',
  '/ERP/approval/allok.do', '/ERP/notice/list.do', '/ERP/notice/view.do',
  '/ERP/notice/write.do'
])

async function getJson(path) {
  const response = await fetch(`${BASE}${path}`, { headers: { Accept: 'application/json' } })
  if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
    window.location.replace(`${BASE}/login/login.do`)
    throw new Error('로그인이 필요합니다.')
  }
  if (!response.ok) throw new Error('데이터를 불러오지 못했습니다.')
  return response.json()
}

async function postJson(path, body, csrf) {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      [csrf.headerName]: csrf.token
    },
    body: JSON.stringify(body)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || '요청을 처리하지 못했습니다.')
  }
  return response.json()
}

function currentRoute() {
  return { path: window.location.pathname, search: window.location.search }
}

function isLandingPath(path) {
  return path === BASE || path === `${BASE}/`
}

function csrfFromMeta() {
  return {
    parameterName: document.querySelector('meta[name="csrf-parameter"]')?.content || '_csrf',
    headerName: document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN',
    token: document.querySelector('meta[name="csrf-token"]')?.content || ''
  }
}

function initials(name = '') {
  return name.trim().slice(-2) || 'EW'
}

function Icon({ name, size = 20 }) {
  const paths = {
    home: <><path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M9 20v-6h6v6"/></>,
    document: <><path d="M6 2h9l4 4v16H6z"/><path d="M14 2v5h5"/><path d="M9 12h6M9 16h6"/></>,
    inbox: <><path d="M4 5h16v14H4z"/><path d="m4 13 4-4h8l4 4"/><path d="M8 13h8"/></>,
    edit: <><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13 7 4 4"/></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></>,
    users: <><circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2"/><path d="M3 20c0-4 2-7 6-7s6 3 6 7M15 14c3 0 5 2 5 5"/></>,
    menu: <path d="M4 7h16M4 12h16M4 17h16"/>,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    moon: <path d="M20 15a8 8 0 0 1-11-11 9 9 0 1 0 11 11z"/>,
    sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19"/></>,
    chat: <><path d="M4 4h16v13H8l-4 4z"/><path d="M8 9h8M8 13h5"/></>,
    close: <path d="M6 6l12 12M18 6 6 18"/>,
    arrow: <path d="m9 18 6-6-6-6"/>,
    logout: <><path d="M10 5H4v14h6M14 8l4 4-4 4M8 12h10"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    check: <path d="m5 12 4 4L19 6"/>,
    download: <><path d="M12 3v12M7 10l5 5 5-5"/><path d="M5 21h14"/></>,
    trash: <><path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14"/></>
  }
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

export default function App() {
  const [route, setRoute] = useState(currentRoute)
  const [session, setSession] = useState(null)
  const [dashboard, setDashboard] = useState(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const onPopState = () => setRoute(currentRoute())
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  useEffect(() => {
    getJson('/api/session')
      .then((data) => {
        setSession(data)
        if (route.path.includes('/login/login.do') && data.authenticated) {
          window.location.replace(`${BASE}/main.do`)
        } else if (!isLandingPath(route.path) && !route.path.includes('/login/login.do') && !data.authenticated) {
          window.location.replace(`${BASE}/login/login.do`)
        }
      })
      .catch((reason) => setError(reason.message))
  }, [])

  useEffect(() => {
    if (!session?.authenticated) return
    getJson('/api/dashboard').then(setDashboard).catch((reason) => setError(reason.message))
  }, [session?.authenticated])

  function navigate(href) {
    const url = new URL(href, window.location.origin)
    if (!APP_PATHS.has(url.pathname)) {
      window.location.href = url.href
      return
    }
    window.history.pushState({}, '', url.pathname + url.search)
    setRoute(currentRoute())
    setDrawerOpen(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (isLandingPath(route.path)) {
    return <LandingPage authenticated={session?.authenticated} />
  }

  if (route.path.includes('/login/login.do')) {
    return <LoginPage csrf={session?.csrf || csrfFromMeta()} />
  }

  if (!session?.authenticated) {
    return <PageState message={error || '업무 공간을 준비하고 있습니다.'} />
  }

  return (
    <AppLayout
      session={session}
      dashboard={dashboard}
      route={route}
      navigate={navigate}
      drawerOpen={drawerOpen}
      setDrawerOpen={setDrawerOpen}
    >
      {error && <div className="alert" role="alert">{error}</div>}
      {route.path.endsWith('/main.do') && <Dashboard dashboard={dashboard} navigate={navigate} />}
      {route.path.endsWith('/approval/list.do') && <ApprovalList route={route} navigate={navigate} />}
      {route.path.endsWith('/approval/recv.do') && <ApprovalCollection type="received" route={route} navigate={navigate} />}
      {route.path.endsWith('/approval/allok.do') && <ApprovalCollection type="completed" route={route} navigate={navigate} />}
      {route.path.endsWith('/approval/write.do') && <ApprovalForm session={session} />}
      {route.path.endsWith('/approval/modify.do') && <ApprovalForm session={session} route={route} edit />}
      {route.path.endsWith('/approval/view.do') && <ApprovalDetail route={route} csrf={session.csrf} navigate={navigate} />}
      {route.path.endsWith('/notice/list.do') && <NoticeList session={session} route={route} navigate={navigate} />}
      {route.path.endsWith('/notice/view.do') && <NoticeDetail route={route} csrf={session.csrf} navigate={navigate} />}
      {route.path.endsWith('/notice/write.do') && <NoticeForm csrf={session.csrf} />}
    </AppLayout>
  )
}

function LandingPage({ authenticated }) {
  const workspaceUrl = authenticated ? `${BASE}/main.do` : `${BASE}/login/login.do`
  return (
    <main className="landing-page">
      <header className="landing-header">
        <a className="landing-logo" href={`${BASE}/`}><span>EW</span> EZEN WORKS</a>
        <nav aria-label="랜딩 페이지 메뉴">
          <a href="#features">주요 기능</a>
          <a href="#workflow">업무 흐름</a>
          <a className="landing-login" href={workspaceUrl}>{authenticated ? '업무 공간' : '로그인'}</a>
        </nav>
      </header>

      <section className="landing-hero">
        <div className="landing-copy">
          <p className="landing-eyebrow">SMART WORKSPACE FOR EVERY TEAM</p>
          <h1>결재부터 공지, AI 업무 지원까지<br /> <em>한곳에서 가볍게.</em></h1>
          <p>기존 업무 흐름은 그대로 유지하면서, 더 빠르고 투명하게 협업할 수 있는 사내 업무 공간입니다.</p>
          <div className="landing-actions">
            <a className="landing-primary" href={workspaceUrl}>{authenticated ? '업무 공간 열기' : '로그인하고 시작하기'} <span>→</span></a>
            <a className="landing-secondary" href="#features">기능 살펴보기</a>
          </div>
          <div className="landing-stack"><span>Spring Boot</span><span>React</span><span>MySQL</span><span>Local LLM</span></div>
        </div>

        <div className="landing-preview" aria-label="업무 화면 미리보기">
          <div className="preview-top"><span><b>EW</b> 업무 대시보드</span><i>김사원</i></div>
          <div className="preview-status">
            <div className="blue"><span>결재 대기</span><strong>3</strong></div>
            <div className="yellow"><span>진행 중</span><strong>2</strong></div>
            <div className="green"><span>승인 완료</span><strong>12</strong></div>
          </div>
          <div className="preview-body">
            <div className="preview-document">
              <span className="preview-label">최근 결재</span>
              <strong>신규 프로젝트 장비 구매 품의</strong>
              <div className="preview-line"><span className="done">작성</span><i /><span className="done">검토</span><i /><span>승인</span></div>
            </div>
            <div className="preview-ai"><Icon name="chat" /><div><small>AI 업무 도우미</small><strong>결재 내용을 요약했어요.</strong></div></div>
          </div>
        </div>
      </section>

      <section className="landing-features" id="features">
        <div className="landing-section-heading"><p>CORE FEATURES</p><h2>업무에 필요한 기능만<br />명확하게 담았습니다.</h2></div>
        <div className="feature-grid">
          <article><span><Icon name="document" /></span><h3>전자결재</h3><p>문서 작성부터 결재선 지정, 승인과 반려까지 모든 상태를 한눈에 확인합니다.</p></article>
          <article><span><Icon name="users" /></span><h3>권한 관리</h3><p>작성자, 결재자, 관리자 역할에 맞춰 문서와 사원 정보 접근을 안전하게 제어합니다.</p></article>
          <article><span><Icon name="bell" /></span><h3>공지와 사원관리</h3><p>사내 소식과 구성원 정보를 한 업무 공간에서 빠르게 찾고 관리합니다.</p></article>
          <article><span><Icon name="chat" /></span><h3>로컬 AI 도우미</h3><p>사내 데이터가 외부로 나가지 않도록 로컬 LLM 기반 검색과 요약을 준비합니다.</p></article>
        </div>
      </section>

      <section className="landing-workflow" id="workflow">
        <div><p className="landing-eyebrow">SIMPLE WORKFLOW</p><h2>복잡한 결재도<br />세 단계면 충분합니다.</h2></div>
        <ol>
          <li><b>01</b><div><strong>문서 작성</strong><span>필요한 내용을 입력하고 결재선을 지정합니다.</span></div></li>
          <li><b>02</b><div><strong>검토와 결재</strong><span>결재자가 내용을 확인하고 승인 또는 반려합니다.</span></div></li>
          <li><b>03</b><div><strong>결과 확인</strong><span>처리 상태와 의견을 실시간으로 확인합니다.</span></div></li>
        </ol>
      </section>

      <section className="landing-cta">
        <div><p>EZEN WORKS</p><h2>더 맑고 가벼운 업무 흐름을<br />지금 시작해 보세요.</h2></div>
        <a href={workspaceUrl}>{authenticated ? '업무 공간 열기' : '로그인하기'} →</a>
      </section>

      <footer className="landing-footer"><strong>EZEN WORKS</strong><span>기업 전자결재 웹 서비스</span><small>© 2026 EZEN Company</small></footer>
    </main>
  )
}

function LoginPage({ csrf }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const body = new URLSearchParams()
    body.set('usernum', form.get('usernum'))
    body.set('userpw', form.get('userpw'))
    body.set(csrf.parameterName, csrf.token)
    setLoading(true)
    setError('')
    try {
      const response = await fetch(`${BASE}/login/login.do`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body
      })
      const result = (await response.text()).trim()
      if (result === 'OK') window.location.replace(`${BASE}/main.do`)
      else if (result === 'CHANGE') window.location.replace(`${BASE}/login/changepw.do`)
      else setError('사원번호 또는 비밀번호를 확인해 주세요.')
    } catch {
      setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="login-card">
          <form onSubmit={submit} className="login-form">
            <label>사원번호<input name="usernum" autoComplete="username" autoFocus required /></label>
            <label>비밀번호<input type="password" name="userpw" autoComplete="current-password" required /></label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="primary-button login-button" disabled={loading}>{loading ? '로그인 중…' : 'Login'}</button>
          </form>
          <p className="welcome-text">ezen소프트웨어에 오신 것을 환영합니다.</p>
        </div>
      </section>
    </main>
  )
}

function AppLayout({ session, dashboard, route, navigate, drawerOpen, setDrawerOpen, children }) {
  const [dark, setDark] = useState(document.documentElement.dataset.theme === 'dark')
  const [chatOpen, setChatOpen] = useState(false)
  const [messengerOpen, setMessengerOpen] = useState(false)

  function toggleTheme() {
    const next = !dark
    setDark(next)
    document.documentElement.dataset.theme = next ? 'dark' : 'light'
    localStorage.setItem('erp-theme', next ? 'dark' : 'light')
  }

  return (
    <div className="app-shell workspace-shell">
      <Sidebar user={session.user} counts={dashboard?.counts} route={route} navigate={navigate} open={drawerOpen} close={() => setDrawerOpen(false)} csrf={session.csrf} />
      {drawerOpen && <button className="drawer-backdrop" aria-label="메뉴 닫기" onClick={() => setDrawerOpen(false)} />}
      <div className="app-column">
        <header className="mobile-topbar">
          <button className="icon-button menu-button" onClick={() => setDrawerOpen(true)} aria-label="메뉴 열기"><Icon name="menu" /></button>
          <strong>EZEN</strong>
        </header>
        <main className="workspace">{children}</main>
      </div>
      <div className="floating-actions">
        <button className="floating-button secondary" onClick={toggleTheme} aria-label={dark ? '라이트 모드 전환' : '다크 모드 전환'}><Icon name={dark ? 'sun' : 'moon'} /></button>
        <Messenger user={session.user} csrf={session.csrf} open={messengerOpen} toggle={() => { setMessengerOpen((value) => !value); setChatOpen(false) }} />
		<button className="floating-button chat-button" onClick={() => { setChatOpen((open) => !open); setMessengerOpen(false) }} aria-label={chatOpen ? 'AI 챗봇 닫기' : 'AI 챗봇 열기'}><Icon name={chatOpen ? 'close' : 'chat'} /></button>
      </div>
      {chatOpen && <ChatPanel close={() => setChatOpen(false)} />}
    </div>
  )
}

function Sidebar({ user, counts = {}, route, navigate, open, close, csrf }) {
  const items = [
    { label: '결재대기', icon: 'document', href: `${BASE}/approval/list.do?mode=1`, badge: counts['대기중'] },
    { label: '결재반려', icon: 'document', href: `${BASE}/approval/list.do?mode=2`, badge: counts['반려'] },
    { label: '결재진행', icon: 'document', href: `${BASE}/approval/list.do?mode=3`, badge: counts['진행중'] },
    { label: '결재승인', icon: 'document', href: `${BASE}/approval/list.do?mode=4`, badge: counts['승인'] },
    { label: '결재수신', icon: 'inbox', href: `${BASE}/approval/recv.do`, badge: counts['수신'] },
    { label: '결재 작성', icon: 'edit', href: `${BASE}/approval/write.do` },
    { label: '전체 승인 내역', icon: 'check', href: `${BASE}/approval/allok.do` },
    { label: '공지사항', icon: 'bell', href: `${BASE}/notice/list.do` },
    ...(user.authority ? [{ label: '사원 관리', icon: 'users', href: `${BASE}/user/list.do` }] : [])
  ]
  const activeMode = new URLSearchParams(route.search).get('mode')
  const isActive = (href) => href.includes('/approval/list.do')
    ? href.endsWith(`mode=${activeMode}`)
    : route.path === new URL(href, window.location.origin).pathname

  return (
    <aside className={`sidebar workspace-sidebar ${open ? 'open' : ''}`}>
      <div className="sidebar-heading">
        <a className="brand" href={`${BASE}/main.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/main.do`) }}>EZEN</a>
        <button className="icon-button sidebar-close" onClick={close} aria-label="메뉴 닫기"><Icon name="close" /></button>
      </div>
      <div className="sidebar-profile">
        <span className="avatar">{initials(user.name)}</span>
        <div><strong>{user.name}</strong><small>ezen소프트웨어</small><small>{user.team} · {user.level}</small></div>
        <div className="sidebar-profile-actions">
          <a href={`${BASE}/user/myinfo.do`}>내 정보</a>
          <form action={`${BASE}/login/logout.do`} method="post">
            <input type="hidden" name={csrf.parameterName} value={csrf.token} />
            <button>로그아웃</button>
          </form>
        </div>
      </div>
      <nav className="main-nav" aria-label="주요 메뉴">
        <p className="nav-label">내 결재관리</p>
        {items.map((item) => (
          <a key={item.label} className={`nav-link ${isActive(item.href) ? 'active' : ''}`} href={item.href} onClick={(event) => { if (APP_PATHS.has(new URL(item.href, window.location.origin).pathname)) { event.preventDefault(); navigate(item.href) } }}>
            <Icon name={item.icon} /><span>{item.label}</span>{Number.isInteger(item.badge) && <b>{item.badge}</b>}
          </a>
        ))}
      </nav>
    </aside>
  )
}

function Dashboard({ dashboard, navigate }) {
  if (!dashboard) return <PageState message="오늘의 업무를 불러오는 중입니다." />
  const stats = [
    ['내 수신함', dashboard.counts['수신'] || 0, 'received', null],
    ['대기중', dashboard.counts['대기중'] || 0, 'pending', '1'],
    ['진행중', dashboard.counts['진행중'] || 0, 'progress', '3'],
    ['반려', dashboard.counts['반려'] || 0, 'rejected', '2'],
    ['승인', dashboard.counts['승인'] || 0, 'approved', '4']
  ]
  return (
    <>
      <section className="legacy-main-heading">
        <h1>EZEN</h1>
      </section>
      <section className="legacy-status-panel" aria-label="오늘의 결재 현황">
        <strong>오늘의 결재 현황 &gt;</strong>
        <div className="stat-grid">
          {stats.map(([label, value, tone, mode]) => <button key={label} className={`stat-card ${tone}`} onClick={() => navigate(mode ? `${BASE}/approval/list.do?mode=${mode}` : `${BASE}/approval/recv.do`)}><span>{label}</span><strong>{value}<small>건</small></strong></button>)}
        </div>
      </section>
      <section className="legacy-dashboard-grid">
        <LegacyCalendar />
        <article className="surface notice-panel">
          <div className="section-heading"><h2>공지사항 &gt;</h2><a href={`${BASE}/notice/list.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/list.do`) }}>전체 보기</a></div>
          <div className="notice-list">
            {dashboard.notices.length ? dashboard.notices.map((notice) => <a key={notice.noticeNo} href={`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`) }}><span className={`notice-dot ${notice.important ? 'important' : ''}`} /> <strong>{notice.title}</strong><time>{notice.date}</time></a>) : <EmptyState title="새로운 공지가 없습니다." />}
          </div>
        </article>
      </section>
    </>
  )
}

function LegacyCalendar() {
  const [cursor, setCursor] = useState(() => new Date())
  const year = cursor.getFullYear()
  const month = cursor.getMonth()
  const today = new Date()
  const blanks = Array.from({ length: new Date(year, month, 1).getDay() })
  const days = Array.from({ length: new Date(year, month + 1, 0).getDate() }, (_, index) => index + 1)
  const move = (amount) => setCursor(new Date(year, month + amount, 1))

  return (
    <article className="surface legacy-calendar">
      <header>
        <button onClick={() => move(-1)} aria-label="이전 달">&lt;</button>
        <h2>{year}년 {month + 1}월</h2>
        <button onClick={() => move(1)} aria-label="다음 달">&gt;</button>
      </header>
      <div className="calendar-weekdays">{['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <span key={day}>{day}</span>)}</div>
      <div className="calendar-days">
        {blanks.map((_, index) => <span key={`blank-${index}`} />)}
        {days.map((day) => <span key={day} className={year === today.getFullYear() && month === today.getMonth() && day === today.getDate() ? 'today' : ''}>{day}</span>)}
      </div>
    </article>
  )
}

function ApprovalList({ route, navigate }) {
  const query = useMemo(() => new URLSearchParams(route.search), [route.search])
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [kind, setKind] = useState(query.get('kind') || '')
  const [mode, setMode] = useState(query.get('mode') || '0')
  const [keyword, setKeyword] = useState(query.get('keyword') || '')

  useEffect(() => {
    setKind(query.get('kind') || '')
    setMode(query.get('mode') || '0')
    setKeyword(query.get('keyword') || '')
  }, [route.search])

  useEffect(() => {
    setData(null)
    setError('')
    getJson(`/api/approvals${route.search || '?mode=0'}`).then(setData).catch((reason) => setError(reason.message))
  }, [route.search])

  function applyFilters(event, page = 1) {
    event?.preventDefault()
    const params = new URLSearchParams({ mode, page: String(page) })
    if (kind) params.set('kind', kind)
    if (keyword.trim()) params.set('keyword', keyword.trim())
    navigate(`${BASE}/approval/list.do?${params}`)
  }

  const titles = { '0': '전체 문서', '1': '결재 대기', '2': '반려 문서', '3': '진행 문서', '4': '승인 문서' }
  return (
    <>
      <section className="page-heading">
        <div><p className="page-kicker">MY APPROVAL</p><h1>내 결재 관리</h1><p className="muted">작성한 문서의 처리 상태를 한눈에 확인하세요.</p></div>
        <a className="primary-button" href={`${BASE}/approval/write.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/write.do`) }}><Icon name="plus" /> 새 결재 작성</a>
      </section>
      <section className="surface list-surface">
        <div className="list-title"><div><h2>{titles[mode] || '전체 문서'}</h2><span>{data ? `총 ${data.totalCount}건` : '불러오는 중'}</span></div></div>
        <form className="filter-bar" onSubmit={applyFilters}>
          <label><span className="sr-only">문서 구분</span><select value={kind} onChange={(event) => setKind(event.target.value)}><option value="">모든 문서</option><option>품의서</option><option>기안서</option><option>연차신청서</option></select></label>
          <label><span className="sr-only">결재 상태</span><select value={mode} onChange={(event) => setMode(event.target.value)}><option value="0">모든 상태</option><option value="1">대기중</option><option value="2">반려</option><option value="3">진행중</option><option value="4">승인</option></select></label>
          <label className="search-field"><Icon name="search" size={18} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="문서 제목 검색" /></label>
          <button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton /> : data.items.length ? <div className="approval-cards">{data.items.map((item) => <ApprovalCard key={item.approvalNo} item={item} navigate={navigate} />)}</div> : <EmptyState title="조건에 맞는 결재 문서가 없습니다." description="검색 조건을 바꾸거나 새 문서를 작성해 보세요." />}
        {data && data.totalPages > 1 && <nav className="pagination" aria-label="페이지 이동">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => applyFilters(event, page)}>{page}</button>)}</nav>}
      </section>
    </>
  )
}

function ApprovalCard({ item, navigate }) {
  return (
    <a className="approval-card" href={`${BASE}/approval/view.do?approval_no=${item.approvalNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/view.do?approval_no=${item.approvalNo}`) }}>
      <div className="doc-icon"><Icon name="document" /></div>
      <div className="approval-main"><div className="card-meta"><span>{item.kind}</span><time>{item.writeDate}</time></div><h3>{item.title}</h3><p>{item.drafter?.name} · {item.drafter?.team} {item.drafter?.level}</p></div>
      <StatusBadge status={item.status} />
      <Icon name="arrow" />
    </a>
  )
}

function ApprovalCollection({ type, route, navigate }) {
  const query = useMemo(() => new URLSearchParams(route.search), [route.search])
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [kind, setKind] = useState(query.get('kind') || '')
  const [category, setCategory] = useState(query.get(type === 'received' ? 'status' : 'team') || '')
  const [keyword, setKeyword] = useState(query.get('keyword') || '')
  const config = type === 'received'
    ? { title: '결재 수신', description: '내가 결재할 문서와 처리 상태를 확인하세요.', endpoint: 'received', path: 'recv.do', key: 'status' }
    : { title: '전체 승인 내역', description: '승인이 완료된 문서를 부서별로 확인하세요.', endpoint: 'completed', path: 'allok.do', key: 'team' }

  useEffect(() => {
    setKind(query.get('kind') || '')
    setCategory(query.get(config.key) || '')
    setKeyword(query.get('keyword') || '')
  }, [type, route.search])

  useEffect(() => {
    setData(null)
    setError('')
    getJson(`/api/approvals/${config.endpoint}${route.search}`).then(setData).catch((reason) => setError(reason.message))
  }, [type, route.search])

  function applyFilters(event, page = 1) {
    event?.preventDefault()
    const params = new URLSearchParams({ page: String(page) })
    if (kind) params.set('kind', kind)
    if (category) params.set(config.key, category)
    if (keyword.trim()) params.set('keyword', keyword.trim())
    navigate(`${BASE}/approval/${config.path}?${params}`)
  }

  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">APPROVAL</p><h1>{config.title}</h1><p className="muted">{config.description}</p></div></section>
      <section className="surface list-surface">
        <div className="list-title"><div><h2>{config.title}</h2><span>{data ? `총 ${data.totalCount}건` : '불러오는 중'}</span></div></div>
        <form className="filter-bar" onSubmit={applyFilters}>
          <select value={kind} onChange={(event) => setKind(event.target.value)} aria-label="문서 구분"><option value="">모든 문서</option><option>품의서</option><option>기안서</option><option>연차신청서</option></select>
          <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label={type === 'received' ? '결재 상태' : '부서'}>
            <option value="">{type === 'received' ? '모든 상태' : '모든 부서'}</option>
            {(type === 'received' ? ['대기중', '진행중', '승인', '반려'] : ['개발', '디자인', '경영지원']).map((value) => <option key={value}>{value}</option>)}
          </select>
          <label className="search-field"><Icon name="search" size={18} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="문서 제목 검색" /></label>
          <button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton /> : data.items.length ? <div className="approval-cards">{data.items.map((item) => <ApprovalCard key={item.approvalNo} item={item} navigate={navigate} />)}</div> : <EmptyState title="조건에 맞는 결재 문서가 없습니다." />}
        {data && data.totalPages > 1 && <nav className="pagination">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => applyFilters(event, page)}>{page}</button>)}</nav>}
      </section>
    </>
  )
}

function ApprovalForm({ session, route, edit = false }) {
  const approvalNo = edit ? new URLSearchParams(route.search).get('approval_no') : null
  const [options, setOptions] = useState(null)
  const [detail, setDetail] = useState(null)
  const [targets, setTargets] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    const requests = [getJson('/api/approvals/write-options')]
    if (edit) requests.push(getJson(`/api/approvals/${approvalNo}`))
    Promise.all(requests).then(([nextOptions, nextDetail]) => {
      setOptions(nextOptions)
      setDetail(nextDetail || null)
      setTargets(nextDetail?.lines.map((line) => line.target) || [])
    }).catch((reason) => setError(reason.message))
  }, [approvalNo, edit])

  function toggleTarget(usernum) {
    setTargets((items) => items.includes(usernum) ? items.filter((item) => item !== usernum) : [...items, usernum])
  }

  function validate(event) {
    if (!targets.length) {
      event.preventDefault()
      setError('결재자를 한 명 이상 선택해 주세요.')
    }
  }

  if (error && !options) return <PageState message={error} />
  if (!options || (edit && !detail)) return <PageState message="결재 작성 화면을 준비하고 있습니다." />
  const item = detail?.item
  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">APPROVAL FORM</p><h1>{edit ? '결재 문서 수정' : '결재 작성'}</h1><p className="muted">문서를 작성하고 결재선을 지정하세요.</p></div></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <form className="surface workspace-form" action={`${BASE}/approval/${edit ? 'modify.do' : 'write.do'}`} method="post" encType="multipart/form-data" onSubmit={validate}>
        <input type="hidden" name={session.csrf.parameterName} value={session.csrf.token} />
        {edit && <input type="hidden" name="approval_no" value={approvalNo} />}
        {targets.map((target) => <input key={target} type="hidden" name="approval_target" value={target} />)}
        <div className="form-grid three">
          <label>문서 구분<select name="kind" defaultValue={item?.kind || ''} required><option value="" disabled>선택해 주세요</option><option>연차신청서</option><option>품의서</option><option>기안서</option></select></label>
          <label>품의 번호<input name="approval_code" defaultValue={item?.code || ''} placeholder="예: EW-2026-001" /></label>
          <label>작성일<input name="writedate" value={options.date} readOnly /></label>
        </div>
        <div className="form-drafter"><span className="avatar">{initials(options.drafter.name)}</span><div><small>기안자</small><strong>{options.drafter.name}</strong><span>{options.drafter.team} · {options.drafter.level}</span></div></div>
        <label className="form-field">제목<input name="approval_title" defaultValue={item?.title || ''} placeholder="제목을 입력해 주세요" required /></label>
        <label className="form-field">내용<textarea name="approval_content" defaultValue={item?.content || ''} placeholder="내용을 입력해 주세요" required /></label>
        <fieldset className="approver-picker"><legend>결재선</legend><div>{options.approvers.map((person) => <label key={person.usernum} className={targets.includes(person.usernum) ? 'selected' : ''}><input type="checkbox" checked={targets.includes(person.usernum)} onChange={() => toggleTarget(person.usernum)} /><span className="avatar small">{initials(person.name)}</span><span><strong>{person.name}</strong><small>{person.team} · {person.level}</small></span></label>)}</div></fieldset>
        <label className="form-field">첨부파일<input type="file" name="attach" /></label>
        {edit && detail.files.length > 0 && <p className="muted">현재 파일: {detail.files.map((file) => file.name).join(', ')}</p>}
        <div className="form-actions"><a className="secondary-button" href={`${BASE}/approval/list.do?mode=0`}>취소</a><button className="primary-button">{edit ? '수정 완료' : '등록 완료'}</button></div>
      </form>
    </>
  )
}

function ApprovalDetail({ route, csrf, navigate }) {
  const id = new URLSearchParams(route.search).get('approval_no')
  const [data, setData] = useState(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const load = () => getJson(`/api/approvals/${id}`).then(setData).catch((reason) => setError(reason.message))
	useEffect(() => { load() }, [id])

  async function submitAction(status) {
    if (status === '반려' && !comment.trim()) {
      setError('반려 의견을 입력해 주세요.')
      return
    }
    const body = new URLSearchParams({ approval_no: id, approval_status: status, comment })
    body.set(csrf.parameterName, csrf.token)
    setSaving(true)
    setError('')
    const response = await fetch(`${BASE}/approval/updateComment.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
    if (!response.ok) setError((await response.text()) || '처리하지 못했습니다.')
    else { setComment(''); await load() }
    setSaving(false)
  }

  async function remove() {
    if (!window.confirm('문서를 삭제하시겠습니까?')) return
    const body = new URLSearchParams({ approval_no: id })
    body.set(csrf.parameterName, csrf.token)
    const response = await fetch(`${BASE}/approval/delete.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
    if (response.ok) navigate(`${BASE}/approval/list.do?mode=0`)
    else setError('문서를 삭제하지 못했습니다.')
  }

  if (error && !data) return <PageState message={error} />
  if (!data) return <PageState message="결재 문서를 불러오는 중입니다." />
  const item = data.item
  return (
    <>
      <section className="page-heading compact"><div><button className="text-button" onClick={() => navigate(`${BASE}/approval/list.do?mode=0`)}>← 목록으로</button><p className="page-kicker">APPROVAL DETAIL</p><h1>{item.title}</h1></div><StatusBadge status={item.status} /></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <article className="surface document-surface">
        <div className="document-header">
          <div className="document-number"><span>{item.kind}</span><strong>{item.code || `DOC-${item.approvalNo}`}</strong></div>
          <div className="drafter"><span className="avatar">{initials(item.drafter?.name)}</span><div><small>기안자</small><strong>{item.drafter?.name}</strong><span>{item.drafter?.team} · {item.drafter?.level}</span></div></div>
        </div>
        <dl className="document-info"><div><dt>작성일</dt><dd>{item.writeDate}</dd></div><div><dt>문서 번호</dt><dd>#{item.approvalNo}</dd></div><div><dt>문서 상태</dt><dd>{item.status}</dd></div></dl>
        <section className="document-content"><h2>기안 내용</h2><div>{item.content || '내용이 없습니다.'}</div></section>
        <section className="approval-route"><div className="section-heading"><div><p className="page-kicker">APPROVAL LINE</p><h2>결재선</h2></div></div><div className="approval-line-list">{data.lines.map((line, index) => <div className="approval-person" key={`${line.target}-${index}`}><span className="line-order">{index + 1}</span><span className="avatar">{initials(line.approver?.name)}</span><div><strong>{line.approver?.name}</strong><small>{line.approver?.team} · {line.approver?.level}</small>{line.comment && <p>“{line.comment}”</p>}</div><div className="line-result"><StatusBadge status={line.status === '대기' ? '대기중' : line.status} /><time>{line.date || '처리 전'}</time></div></div>)}</div></section>
        <section className="attachments"><h2>첨부파일</h2>{data.files.length ? data.files.map((file) => <a key={file.name} href={file.downloadUrl}><Icon name="download" /> {file.name}</a>) : <p className="muted">첨부파일이 없습니다.</p>}</section>
        {data.canApprove && <section className="decision-box"><label>결재 의견<textarea value={comment} onChange={(event) => setComment(event.target.value)} placeholder="승인 또는 반려 의견을 입력하세요" /></label><div><button className="secondary-button danger" onClick={() => submitAction('반려')} disabled={saving}>반려</button><button className="primary-button" onClick={() => submitAction('승인')} disabled={saving}><Icon name="check" /> 승인</button></div></section>}
        {(data.canEdit || data.canDelete) && <div className="document-actions">{data.canDelete && <button className="text-button danger" onClick={remove}><Icon name="trash" /> 삭제</button>}{data.canEdit && <a className="secondary-button" href={`${BASE}/approval/modify.do?approval_no=${item.approvalNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/modify.do?approval_no=${item.approvalNo}`) }}><Icon name="edit" /> 수정</a>}</div>}
      </article>
    </>
  )
}

function NoticeList({ session, route, navigate }) {
  const query = useMemo(() => new URLSearchParams(route.search), [route.search])
  const [data, setData] = useState(null)
  const [team, setTeam] = useState(query.get('notice_team') || '')
  const [keyword, setKeyword] = useState(query.get('searchWord') || '')
  const [error, setError] = useState('')

  useEffect(() => {
    setTeam(query.get('notice_team') || '')
    setKeyword(query.get('searchWord') || '')
  }, [route.search])

  useEffect(() => {
    setData(null)
    setError('')
    getJson(`/api/notices${route.search}`).then(setData).catch((reason) => setError(reason.message))
  }, [route.search])

  function applyFilters(event, page = 1) {
    event?.preventDefault()
    const params = new URLSearchParams({ page: String(page) })
    if (team) params.set('notice_team', team)
    if (keyword.trim()) params.set('searchWord', keyword.trim())
    navigate(`${BASE}/notice/list.do?${params}`)
  }

  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">NOTICE</p><h1>공지사항</h1><p className="muted">회사 소식과 주요 안내를 확인하세요.</p></div>{session.user.authority && <a className="primary-button" href={`${BASE}/notice/write.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/write.do`) }}><Icon name="plus" /> 공지 등록</a>}</section>
      <section className="surface list-surface">
        <div className="list-title"><div><h2>공지 목록</h2><span>{data ? `총 ${data.totalCount}건` : '불러오는 중'}</span></div></div>
        <form className="filter-bar notice-filter" onSubmit={applyFilters}>
          <select value={team} onChange={(event) => setTeam(event.target.value)} aria-label="공지 대상"><option value="">모든 부서</option><option value="100">개발</option><option value="200">디자인</option><option value="300">경영지원</option></select>
          <label className="search-field"><Icon name="search" size={18} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="공지 제목 검색" /></label>
          <button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton /> : data.items.length ? <div className="notice-cards">{data.items.map((notice) => <a key={notice.noticeNo} href={`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`) }}><span className={`notice-dot ${notice.important ? 'important' : ''}`} /><div><small>{notice.important ? '중요 공지' : '공지사항'}</small><strong>{notice.title}</strong><span>{notice.writer || '관리자'}</span></div><time>{notice.date}</time><Icon name="arrow" /></a>)}</div> : <EmptyState title="등록된 공지사항이 없습니다." />}
        {data && data.totalPages > 1 && <nav className="pagination">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => applyFilters(event, page)}>{page}</button>)}</nav>}
      </section>
    </>
  )
}

function NoticeDetail({ route, csrf, navigate }) {
  const noticeNo = new URLSearchParams(route.search).get('notice_no')
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => { getJson(`/api/notices/${noticeNo}`).then(setNotice).catch((reason) => setError(reason.message)) }, [noticeNo])

  async function remove() {
    if (!window.confirm('공지사항을 삭제하시겠습니까?')) return
    const body = new URLSearchParams({ notice_no: noticeNo, [csrf.parameterName]: csrf.token })
    const response = await fetch(`${BASE}/notice/delete.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
    if (response.ok) navigate(`${BASE}/notice/list.do`)
    else setError('공지사항을 삭제하지 못했습니다.')
  }

  if (error && !notice) return <PageState message={error} />
  if (!notice) return <PageState message="공지사항을 불러오는 중입니다." />
  return (
    <>
      <section className="page-heading compact"><div><button className="text-button" onClick={() => navigate(`${BASE}/notice/list.do`)}>← 목록으로</button><p className="page-kicker">NOTICE DETAIL</p><h1>{notice.title}</h1></div>{notice.important && <span className="status-badge rejected"><i />중요</span>}</section>
      {error && <div className="alert">{error}</div>}
      <article className="surface document-surface notice-detail">
        <dl className="document-info"><div><dt>작성자</dt><dd>{notice.writer || '관리자'}</dd></div><div><dt>작성일</dt><dd>{notice.date}</dd></div><div><dt>공지 대상</dt><dd>{notice.teams.join(', ') || '전체'}</dd></div></dl>
        <section className="document-content"><h2>공지 내용</h2><div>{notice.content || '내용이 없습니다.'}</div></section>
        <section className="attachments"><h2>첨부파일</h2>{notice.downloadUrl ? <a href={notice.downloadUrl}><Icon name="download" /> {notice.fileName}</a> : <p className="muted">첨부파일이 없습니다.</p>}</section>
        {notice.canDelete && <div className="document-actions"><button className="text-button danger" onClick={remove}><Icon name="trash" /> 삭제</button></div>}
      </article>
    </>
  )
}

function NoticeForm({ csrf }) {
  const [teams, setTeams] = useState([])
  const [error, setError] = useState('')
  function toggleTeam(team) {
    setTeams((items) => items.includes(team) ? items.filter((item) => item !== team) : [...items, team])
  }
  function validate(event) {
    if (!teams.length) {
      event.preventDefault()
      setError('공지 대상을 한 곳 이상 선택해 주세요.')
    }
  }
  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">NOTICE FORM</p><h1>공지 등록</h1><p className="muted">구성원에게 전달할 공지 내용을 작성하세요.</p></div></section>
      {error && <div className="alert">{error}</div>}
      <form className="surface workspace-form" action={`${BASE}/notice/writeOK.do`} method="post" encType="multipart/form-data" onSubmit={validate}>
        <input type="hidden" name={csrf.parameterName} value={csrf.token} />
        {teams.map((team) => <input key={team} type="hidden" name="notice_team" value={team} />)}
        <label className="form-field">제목<input name="notice_title" placeholder="제목을 입력해 주세요" autoFocus required /></label>
        <div className="check-row"><label><input type="checkbox" name="is_important" value="1" /> 중요 공지</label><label><input type="checkbox" name="is_main" value="1" /> 메인 화면 노출</label></div>
        <fieldset className="approver-picker"><legend>공지 대상</legend><div>{[['100', '개발'], ['200', '디자인'], ['300', '경영지원']].map(([value, label]) => <label key={value} className={teams.includes(value) ? 'selected' : ''}><input type="checkbox" checked={teams.includes(value)} onChange={() => toggleTeam(value)} /><span><strong>{label}</strong><small>부서 구성원</small></span></label>)}</div></fieldset>
        <label className="form-field">내용<textarea name="notice_content" placeholder="내용을 입력해 주세요" required /></label>
        <label className="form-field">첨부파일<input type="file" name="fileInput" /></label>
        <div className="form-actions"><a className="secondary-button" href={`${BASE}/notice/list.do`}>취소</a><button className="primary-button">등록 완료</button></div>
      </form>
    </>
  )
}

function StatusBadge({ status }) {
  const tone = { 승인: 'approved', 진행중: 'progress', 반려: 'rejected', 대기중: 'pending' }[status] || 'pending'
  return <span className={`status-badge ${tone}`}><i />{status}</span>
}

function Messenger({ user, csrf, open, toggle }) {
  const clientRef = useRef(null)
  const selectedRoomRef = useRef(null)
  const messageEndRef = useRef(null)
  const [rooms, setRooms] = useState([])
  const [contacts, setContacts] = useState([])
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [messages, setMessages] = useState([])
  const [partner, setPartner] = useState('')
  const [content, setContent] = useState('')
  const [status, setStatus] = useState('연결 중')
  const [error, setError] = useState('')

  const loadRooms = () => getJson('/api/chat/rooms').then((data) => {
    setRooms(data)
    return data
  })

  const markRead = (roomNo) => postJson(`/api/chat/rooms/${roomNo}/read`, {}, csrf)
    .then(loadRooms)
    .catch((reason) => setError(reason.message))

  useEffect(() => {
    selectedRoomRef.current = selectedRoom
  }, [selectedRoom])

  useEffect(() => {
    Promise.all([loadRooms(), getJson('/api/chat/contacts').then(setContacts)])
      .catch((reason) => setError(reason.message))

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}${BASE}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {}
    })
    client.onConnect = () => {
      setStatus('연결됨')
      setError('')
      loadRooms().catch((reason) => setError(reason.message))
      const current = selectedRoomRef.current
      if (current) {
        getJson(`/api/chat/rooms/${current.roomNo}/messages`).then(setMessages)
          .catch((reason) => setError(reason.message))
      }
      client.subscribe('/user/queue/messages', ({ body }) => {
        const message = JSON.parse(body)
        loadRooms().catch(() => {})
        if (selectedRoomRef.current?.roomNo === message.roomNo) {
          setMessages((items) => items.some((item) => item.messageNo === message.messageNo)
            ? items : [...items, message])
          if (message.senderUsernum !== user.usernum) markRead(message.roomNo)
        }
      })
      client.subscribe('/user/queue/read', ({ body }) => {
        const receipt = JSON.parse(body)
        if (selectedRoomRef.current?.roomNo === receipt.roomNo) {
          setMessages((items) => items.map((message) => message.senderUsernum === user.usernum
            ? { ...message, readAt: receipt.readAt } : message))
        }
      })
      client.subscribe('/user/queue/errors', ({ body }) => {
        setError(JSON.parse(body).message || '메시지를 처리하지 못했습니다.')
      })
    }
    client.onWebSocketClose = () => setStatus('재연결 중')
    client.onStompError = () => setError('메신저 서버에 연결하지 못했습니다.')
    client.activate()
    clientRef.current = client
    return () => { client.deactivate() }
  }, [user.usernum, csrf.headerName, csrf.token])

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  async function openConversation(room) {
    setSelectedRoom(room)
    selectedRoomRef.current = room
    setError('')
    try {
      setMessages(await getJson(`/api/chat/rooms/${room.roomNo}/messages`))
      await markRead(room.roomNo)
    } catch (reason) {
      setError(reason.message)
    }
  }

  async function startConversation(event) {
    event.preventDefault()
    if (!partner) return
    try {
      const { roomNo } = await postJson('/api/chat/rooms', { partnerUsernum: partner }, csrf)
      const freshRooms = await loadRooms()
      await openConversation(freshRooms.find((room) => room.roomNo === roomNo))
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
    clientRef.current.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ roomNo: selectedRoom.roomNo, content: message })
    })
    setContent('')
  }

  const unreadCount = rooms.reduce((sum, room) => sum + room.unreadCount, 0)

  return (
    <>
      <button className="floating-button messenger-button" onClick={toggle} aria-label={open ? '사내 메신저 닫기' : '사내 메신저 열기'}>
        <Icon name={open ? 'close' : 'users'} />{unreadCount > 0 && <b>{unreadCount > 99 ? '99+' : unreadCount}</b>}
      </button>
      {open && <aside className="chat-panel messenger-panel" aria-label="사내 메신저">
        <header>
          <div>{selectedRoom && <button className="messenger-back" onClick={() => setSelectedRoom(null)} aria-label="대화방 목록">←</button>}<span className="chat-avatar"><Icon name="users" /></span><div><strong>{selectedRoom?.partnerName || '사내 메신저'}</strong><small>{status}</small></div></div>
          <button className="icon-button" onClick={toggle} aria-label="메신저 닫기"><Icon name="close" /></button>
        </header>
        {error && <p className="messenger-error" role="alert">{error}</p>}
        {!selectedRoom ? <div className="messenger-lobby">
          <form className="messenger-new" onSubmit={startConversation}>
            <select value={partner} onChange={(event) => setPartner(event.target.value)} aria-label="대화 상대" required>
              <option value="">대화 상대 선택</option>
              {contacts.map((contact) => <option key={contact.usernum} value={contact.usernum}>{contact.name} · {contact.team} {contact.level}</option>)}
            </select>
            <button>대화 시작</button>
          </form>
          <div className="messenger-room-list">
            {rooms.length ? rooms.map((room) => <button key={room.roomNo} onClick={() => openConversation(room)}>
              <span className="avatar small">{initials(room.partnerName)}</span>
              <span><strong>{room.partnerName}</strong><small>{room.lastMessage || '새 대화를 시작해 보세요.'}</small></span>
              <span>{room.lastMessageAt?.slice(5, 16) || ''}{room.unreadCount > 0 && <b>{room.unreadCount}</b>}</span>
            </button>) : <p className="messenger-empty">아직 시작한 대화가 없습니다.</p>}
          </div>
        </div> : <div className="messenger-conversation">
          <div className="messenger-messages" aria-live="polite">
            {messages.length ? messages.map((message) => <div key={message.messageNo} className={`messenger-message ${message.senderUsernum === user.usernum ? 'mine' : ''}`}>
              <p>{message.content}</p><small>{message.sentAt?.slice(11, 16)}{message.senderUsernum === user.usernum && ` · ${message.readAt ? '읽음' : '안 읽음'}`}</small>
            </div>) : <p className="messenger-empty">첫 메시지를 보내 보세요.</p>}
            <span ref={messageEndRef} />
          </div>
          <form className="messenger-compose" onSubmit={send}>
            <input value={content} onChange={(event) => setContent(event.target.value)} maxLength="2000" placeholder="메시지를 입력하세요" aria-label="메시지" />
            <button aria-label="메시지 보내기">↑</button>
          </form>
        </div>}
      </aside>}
    </>
  )
}

function ChatPanel({ close }) {
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  function submit(event) {
    event.preventDefault()
    if (!message.trim()) return
    setNotice('로컬 LLM API 연결 후 사용할 수 있습니다.')
  }
  return (
    <aside className="chat-panel" aria-label="AI 업무 도우미">
      <header><div><span className="chat-avatar"><Icon name="chat" /></span><div><strong>AI 업무 도우미</strong><small>Local LLM 준비 중</small></div></div><button className="icon-button" onClick={close} aria-label="챗봇 닫기"><Icon name="close" /></button></header>
      <div className="chat-body"><div className="bot-message">안녕하세요. 사내 문서 검색과 결재 요약을 도와드릴 예정입니다.</div><div className="suggestions"><button onClick={() => setMessage('오늘 처리할 결재 알려줘')}>오늘 처리할 결재</button><button onClick={() => setMessage('최근 공지 요약해줘')}>최근 공지 요약</button></div>{notice && <p className="chat-notice">{notice}</p>}</div>
      <form onSubmit={submit}><input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="업무 관련 질문을 입력하세요" aria-label="챗봇 메시지" /><button aria-label="메시지 보내기">↑</button></form>
    </aside>
  )
}

function PageState({ message }) {
  return <div className="page-state"><span className="loader" /><p>{message}</p></div>
}

function EmptyState({ title, description }) {
  return <div className="empty-state"><span><Icon name="document" /></span><strong>{title}</strong>{description && <p>{description}</p>}</div>
}

function CardSkeleton() {
  return <div className="approval-cards" aria-label="문서 로딩 중">{[1, 2, 3].map((item) => <div className="approval-card skeleton" key={item}><i/><div><b/><span/></div></div>)}</div>
}
