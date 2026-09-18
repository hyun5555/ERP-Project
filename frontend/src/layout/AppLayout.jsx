import { useState } from 'react'
import { BASE } from '../api'
import { ChatPanel } from '../ai/ChatPanel'
import { Messenger } from '../messenger/Messenger'
import { Icon, initials } from '../ui'
import './sidebar.css'

export function AppLayout({ session, dashboard, route, navigate, drawerOpen, setDrawerOpen, children }) {
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
  const go = (event, href) => { event.preventDefault(); navigate(href) }

  return (
    <aside className={`sidebar workspace-sidebar ${open ? 'open' : ''}`}>
      <div className="sidebar-heading">
        <a className="brand" href={`${BASE}/main.do`} onClick={(event) => go(event, `${BASE}/main.do`)}>EZEN</a>
        <button className="icon-button sidebar-close" onClick={close} aria-label="메뉴 닫기"><Icon name="close" /></button>
      </div>
      <div className="sidebar-profile">
        <span className="avatar">{initials(user.name)}</span>
        <div><strong>{user.name}</strong><small>ezen소프트웨어</small><small>{user.team} · {user.level}</small></div>
        <div className="sidebar-profile-actions">
          <a href={`${BASE}/user/myinfo.do`} onClick={(event) => go(event, `${BASE}/user/myinfo.do`)}>내 정보</a>
          <form action={`${BASE}/login/logout.do`} method="post">
            <input type="hidden" name={csrf.parameterName} value={csrf.token} />
            <button>로그아웃</button>
          </form>
        </div>
      </div>
      <nav className="main-nav" aria-label="주요 메뉴">
        <p className="nav-label">내 결재관리</p>
        {items.map((item) => (
          <a key={item.label} className={`nav-link ${isActive(item.href) ? 'active' : ''}`} href={item.href} onClick={(event) => go(event, item.href)}>
            <Icon name={item.icon} /><span>{item.label}</span>{Number.isInteger(item.badge) && <b>{item.badge}</b>}
          </a>
        ))}
      </nav>
    </aside>
  )
}
