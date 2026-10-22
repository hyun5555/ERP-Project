import { useEffect, useState } from 'react'
import { BASE, currentRoute, getJson, notifyDashboardChange } from './api'
import { ApprovalCollection, ApprovalDetail, ApprovalForm, ApprovalList } from './approval/ApprovalPages'
import { AppLayout } from './layout/AppLayout'
import { Dashboard } from './layout/Dashboard'
import { LandingPage, LoginPage } from './layout/PublicPages'
import { NoticeDetail, NoticeForm, NoticeList } from './notice/NoticePages'
import { PageState } from './ui'
import { PasswordChange, UserDetail, UserForm, UserList } from './user/UserPages'

const APP_PATHS = new Set([
  '/ERP/main.do', '/ERP/approval/list.do', '/ERP/approval/recv.do',
  '/ERP/approval/write.do', '/ERP/approval/modify.do', '/ERP/approval/view.do',
  '/ERP/approval/allok.do', '/ERP/notice/list.do', '/ERP/notice/view.do',
  '/ERP/notice/write.do', '/ERP/user/list.do', '/ERP/user/view.do',
  '/ERP/user/write.do', '/ERP/user/modify.do', '/ERP/user/myinfo.do',
  '/ERP/login/changepw.do'
])

const isLandingPath = (path) => path === BASE || path === `${BASE}/`

export default function App() {
  const [route, setRoute] = useState(currentRoute)
  const [session, setSession] = useState(null)
  const [dashboard, setDashboard] = useState(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const onPopState = () => {
      const next = currentRoute()
      setRoute(next)
      if (next.path === `${BASE}/main.do`) notifyDashboardChange()
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  useEffect(() => {
    getJson('/api/session').then((data) => {
      setSession(data)
      if (route.path.includes('/login/login.do') && data.authenticated) {
        window.location.replace(`${BASE}/main.do`)
      } else if (!isLandingPath(route.path) && !route.path.includes('/login/login.do') && !data.authenticated) {
        window.location.replace(`${BASE}/login/login.do`)
      }
    }).catch((reason) => setError(reason.message))
  }, [])

  useEffect(() => {
    if (!session?.authenticated) return
    let active = true
    let latest = 0
    const refresh = () => {
      const request = ++latest
      getJson('/api/dashboard').then((data) => { if (active && request === latest) setDashboard(data) })
        .catch((reason) => { if (active && request === latest) setError(reason.message) })
    }
    refresh()
    window.addEventListener('erp:dashboard-changed', refresh)
    return () => { active = false; window.removeEventListener('erp:dashboard-changed', refresh) }
  }, [session?.authenticated])

  function navigate(href) {
    const url = new URL(href, window.location.origin)
    if (!APP_PATHS.has(url.pathname)) {
      window.location.href = url.href
      return
    }
    window.history.pushState({}, '', url.pathname + url.search)
    setRoute(currentRoute())
    if (url.pathname === `${BASE}/main.do`) notifyDashboardChange()
    setDrawerOpen(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (isLandingPath(route.path)) return <LandingPage authenticated={session?.authenticated}/>
  if (route.path.includes('/login/login.do')) {
    return session ? <LoginPage csrf={session.csrf}/> : <PageState message={error || '로그인 화면을 준비하고 있습니다.'}/>
  }
  if (!session?.authenticated) return <PageState message={error || '업무 공간을 준비하고 있습니다.'}/>

  return (
    <AppLayout session={session} dashboard={dashboard} route={route} navigate={navigate} drawerOpen={drawerOpen} setDrawerOpen={setDrawerOpen}>
      {error && <div className="alert" role="alert">{error}</div>}
      {route.path.endsWith('/main.do') && <Dashboard dashboard={dashboard} navigate={navigate}/>}
      {route.path.endsWith('/approval/list.do') && <ApprovalList key={route.search} route={route} navigate={navigate}/>}
      {route.path.endsWith('/approval/recv.do') && <ApprovalCollection key={route.search} type="received" route={route} navigate={navigate}/>}
      {route.path.endsWith('/approval/allok.do') && <ApprovalCollection key={route.search} type="completed" route={route} navigate={navigate}/>}
      {route.path.endsWith('/approval/write.do') && <ApprovalForm session={session}/>}
      {route.path.endsWith('/approval/modify.do') && <ApprovalForm key={route.search} session={session} route={route} edit/>}
      {route.path.endsWith('/approval/view.do') && <ApprovalDetail key={route.search} route={route} csrf={session.csrf} navigate={navigate}/>}
      {route.path.endsWith('/notice/list.do') && <NoticeList key={route.search} session={session} route={route} navigate={navigate}/>}
      {route.path.endsWith('/notice/view.do') && <NoticeDetail key={route.search} route={route} csrf={session.csrf} navigate={navigate}/>}
      {route.path.endsWith('/notice/write.do') && <NoticeForm csrf={session.csrf}/>}
      {route.path.endsWith('/user/list.do') && <UserList key={route.search} route={route} navigate={navigate} csrf={session.csrf}/>}
      {route.path.endsWith('/user/view.do') && <UserDetail key={route.search} route={route} navigate={navigate} csrf={session.csrf}/>}
      {route.path.endsWith('/user/write.do') && <UserForm route={route} navigate={navigate} csrf={session.csrf}/>}
      {route.path.endsWith('/user/modify.do') && <UserForm key={route.search} route={route} navigate={navigate} csrf={session.csrf} edit/>}
      {route.path.endsWith('/user/myinfo.do') && <UserDetail route={route} navigate={navigate} csrf={session.csrf} myInfo/>}
      {route.path.endsWith('/login/changepw.do') && <PasswordChange navigate={navigate} csrf={session.csrf}/>}
    </AppLayout>
  )
}
