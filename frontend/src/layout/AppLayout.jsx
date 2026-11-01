import { useEffect, useRef, useState } from 'react'
import { BASE } from '../api'
import { ChatPanel } from '../ai/ChatPanel'
import { Messenger } from '../messenger/Messenger'
import { Icon, initials } from '../ui'
import './sidebar.css'

export function AppLayout({ session, dashboard, route, navigate, drawerOpen, setDrawerOpen, children }) {
  const [dark, setDark] = useState(document.documentElement.dataset.theme === 'dark')
  const [chatOpen, setChatOpen] = useState(false)
  const [messengerOpen, setMessengerOpen] = useState(false)
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 1080px)').matches)
  const menuButton = useRef(null)
  const sidebar = useRef(null)
  const modalOpen = mobile && drawerOpen

  useEffect(() => {
    const media = window.matchMedia('(max-width: 1080px)')
    const resize = () => {
      if (media.matches && sidebar.current?.contains(document.activeElement)) menuButton.current?.focus()
      setMobile(media.matches)
      setDrawerOpen(false)
    }
    media.addEventListener('change', resize)
    return () => media.removeEventListener('change', resize)
  }, [setDrawerOpen])

  useEffect(() => {
    if (!modalOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    sidebar.current.querySelector('.sidebar-close').focus()
    const handleKey = (event) => {
      if (event.key === 'Escape') { event.preventDefault(); setDrawerOpen(false) }
      if (event.key !== 'Tab') return
      const links = [...sidebar.current.querySelectorAll('a[href], button:not([disabled])')]
        .filter((item) => item.getClientRects().length)
      const first = links[0]
      const last = links.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', handleKey)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKey)
      if (window.matchMedia('(max-width: 1080px)').matches) menuButton.current?.focus()
      else sidebar.current?.querySelector('a[href]')?.focus()
    }
  }, [modalOpen, setDrawerOpen])

  function toggleTheme() {
    const next = !dark
    setDark(next)
    document.documentElement.dataset.theme = next ? 'dark' : 'light'
    localStorage.setItem('erp-theme', next ? 'dark' : 'light')
  }

  return (
    <div className="app-shell workspace-shell">
      <Sidebar user={session.user} counts={dashboard?.counts} route={route} navigate={navigate} open={drawerOpen} mobile={mobile} sidebarRef={sidebar} close={() => setDrawerOpen(false)} csrf={session.csrf} />
      {modalOpen && <button className="drawer-backdrop" tabIndex={-1} aria-hidden="true" onClick={() => setDrawerOpen(false)} />}
      <div className="app-column" inert={modalOpen}>
        <header className="mobile-topbar">
          <button ref={menuButton} className="icon-button menu-button" onClick={() => setDrawerOpen(true)} aria-label="메뉴 열기" aria-expanded={modalOpen} aria-controls="workspace-menu"><Icon name="menu" /></button>
          <strong>EZEN</strong>
        </header>
        <main className="workspace">{children}</main>
      </div>
      <div inert={modalOpen} hidden={modalOpen}>
        <div className="floating-actions">
          <button className="floating-button secondary" onClick={toggleTheme} aria-label={dark ? '라이트 모드 전환' : '다크 모드 전환'}><Icon name={dark ? 'sun' : 'moon'} /></button>
          <Messenger user={session.user} csrf={session.csrf} open={messengerOpen && !modalOpen} toggle={() => { setMessengerOpen((value) => !value); setChatOpen(false) }} />
          <button className="floating-button chat-button" onClick={() => { setChatOpen((open) => !open); setMessengerOpen(false) }} aria-label={chatOpen ? 'AI 챗봇 닫기' : 'AI 챗봇 열기'}><Icon name={chatOpen ? 'close' : 'chat'} /></button>
        </div>
        {chatOpen && <ChatPanel csrf={session.csrf} close={() => setChatOpen(false)} />}
      </div>
    </div>
  )
}

function Sidebar({ user, counts = {}, route, navigate, open, mobile, sidebarRef, close, csrf }) {
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
    <aside ref={sidebarRef} id="workspace-menu" className={`sidebar workspace-sidebar ${open ? 'open' : ''}`} inert={mobile && !open} role={mobile && open ? 'dialog' : undefined} aria-modal={mobile && open ? true : undefined} aria-label={mobile && open ? '전체 메뉴' : undefined}>
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
