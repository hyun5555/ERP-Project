import { useEffect, useMemo, useState } from 'react'
import { BASE, getJson, sendJson } from '../api'
import { EmptyState, Icon, PageState, initials } from '../ui'
import './user.css'

const TEAMS = ['임원', '개발', '디자인', '경영지원']
const LEVELS = ['사장', '팀장', '대리', '사원']
const STATUSES = ['재직', '휴직', '퇴직']

export function UserList({ route, navigate, csrf }) {
  const query = useMemo(() => new URLSearchParams(route.search), [route.search])
  const [data, setData] = useState(null)
  const [selected, setSelected] = useState([])
  const [error, setError] = useState('')
  const [team, setTeam] = useState(query.get('team') || '')
  const [level, setLevel] = useState(query.get('level') || '')
  const [status, setStatus] = useState(query.get('user_status') || '')
  const [searchKey, setSearchKey] = useState(query.get('searchKey') || 'usernum')
  const [searchWord, setSearchWord] = useState(query.get('searchWord') || '')

  const load = () => getJson(`/api/users${route.search}`).then((result) => { setData(result); setSelected([]) })

  useEffect(() => {
    setTeam(query.get('team') || '')
    setLevel(query.get('level') || '')
    setStatus(query.get('user_status') || '')
    setSearchKey(query.get('searchKey') || 'usernum')
    setSearchWord(query.get('searchWord') || '')
  }, [route.search])

  useEffect(() => {
    setData(null)
    setError('')
    load().catch((reason) => setError(reason.message))
  }, [route.search])

  function applyFilters(event, page = 1) {
    event?.preventDefault()
    const params = new URLSearchParams({ page: String(page), searchKey })
    if (team) params.set('team', team)
    if (level) params.set('level', level)
    if (status) params.set('user_status', status)
    if (searchWord.trim()) params.set('searchWord', searchWord.trim())
    navigate(`${BASE}/user/list.do?${params}`)
  }

  function toggle(usernum) {
    setSelected((items) => items.includes(usernum) ? items.filter((item) => item !== usernum) : [...items, usernum])
  }

  async function removeSelected() {
    if (!selected.length) return setError('삭제할 사원을 선택해 주세요.')
    if (!window.confirm(`${selected.length}명의 사원을 삭제하시겠습니까?`)) return
    try {
      await sendJson('/api/users/bulk-delete', 'POST', selected, csrf)
      await load()
    } catch (reason) {
      setError(reason.message)
    }
  }

  const allSelected = Boolean(data?.items.length) && selected.length === data.items.length
  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">EMPLOYEE</p><h1>사원 관리</h1><p className="muted">구성원 정보와 재직 상태를 관리하세요.</p></div><a className="primary-button" href={`${BASE}/user/write.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/user/write.do`) }}><Icon name="plus"/> 사원 등록</a></section>
      <section className="surface list-surface user-list-surface">
        <div className="list-title user-list-title"><div><h2>전체 사원</h2><span>{data ? `총 ${data.totalCount}명` : '불러오는 중'}</span></div><button className="text-button danger" onClick={removeSelected} disabled={!selected.length}><Icon name="trash" size={17}/> 선택 삭제</button></div>
        <form className="filter-bar user-filter" onSubmit={applyFilters}>
          <select value={team} onChange={(event) => setTeam(event.target.value)} aria-label="부서"><option value="">모든 부서</option>{TEAMS.map((item) => <option key={item}>{item}</option>)}</select>
          <select value={level} onChange={(event) => setLevel(event.target.value)} aria-label="직급"><option value="">모든 직급</option>{LEVELS.map((item) => <option key={item}>{item}</option>)}</select>
          <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="근무 상태"><option value="">모든 상태</option>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select>
          <select value={searchKey} onChange={(event) => setSearchKey(event.target.value)} aria-label="검색 기준"><option value="usernum">사원번호</option><option value="name">이름</option></select>
          <label className="search-field"><Icon name="search" size={18}/><input value={searchWord} onChange={(event) => setSearchWord(event.target.value)} placeholder="사원 검색"/></label><button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <PageState message="사원 목록을 불러오는 중입니다."/> : data.items.length ? <div className="user-table-wrap"><table className="user-table"><thead><tr><th><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : data.items.map((item) => item.usernum))} aria-label="전체 선택"/></th><th>사원</th><th>부서</th><th>직급</th><th>내선번호</th><th>이메일</th><th>상태</th><th><span className="sr-only">관리</span></th></tr></thead><tbody>{data.items.map((user) => <tr key={user.usernum} onClick={() => navigate(`${BASE}/user/view.do?usernum=${user.usernum}`)}><td><input type="checkbox" checked={selected.includes(user.usernum)} onChange={() => toggle(user.usernum)} onClick={(event) => event.stopPropagation()} aria-label={`${user.name} 선택`}/></td><td><span className="user-name"><span className="avatar small">{initials(user.name)}</span><span><strong>{user.name}</strong><small>{user.usernum}</small></span></span></td><td>{user.team}</td><td>{user.level}</td><td>{user.officenum || '-'}</td><td>{user.email || '-'}</td><td><span className={`user-status ${user.userStatus}`}>{user.userStatus}</span></td><td><Icon name="arrow" size={17}/></td></tr>)}</tbody></table></div> : <EmptyState title="조건에 맞는 사원이 없습니다."/>}
        {data?.totalPages > 1 && <nav className="pagination">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => applyFilters(event, page)}>{page}</button>)}</nav>}
      </section>
    </>
  )
}

export function UserDetail({ route, navigate, csrf, myInfo = false }) {
  const usernum = new URLSearchParams(route.search).get('usernum')
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setUser(null)
    getJson(myInfo ? '/api/users/me' : `/api/users/${usernum}`).then(setUser).catch((reason) => setError(reason.message))
  }, [usernum, myInfo])

  async function remove() {
    if (!window.confirm('해당 사원을 삭제하시겠습니까?')) return
    try {
      await sendJson(`/api/users/${user.usernum}`, 'DELETE', {}, csrf)
      navigate(`${BASE}/user/list.do`)
    } catch (reason) {
      setError(reason.message)
    }
  }

  if (error && !user) return <PageState message={error}/>
  if (!user) return <PageState message="사원 정보를 불러오는 중입니다."/>
  return (
    <>
      <section className="page-heading compact"><div>{!myInfo && <button className="text-button" onClick={() => navigate(`${BASE}/user/list.do`)}>← 목록으로</button>}<p className="page-kicker">{myInfo ? 'MY PROFILE' : 'EMPLOYEE DETAIL'}</p><h1>{myInfo ? '내 정보' : user.name}</h1></div><span className={`user-status ${user.userStatus}`}>{user.userStatus}</span></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <article className="surface profile-card">
        <header><span className="avatar profile-avatar">{initials(user.name)}</span><div><h2>{user.name}</h2><p>{user.team} · {user.level}</p></div><span>{user.authority ? '관리자' : '일반 사원'}</span></header>
        <dl className="profile-grid"><Profile label="사원번호" value={user.usernum}/><Profile label="입사일자" value={user.joindate?.slice(0, 10)}/><Profile label="전화번호" value={user.phonenum}/><Profile label="내선번호" value={user.officenum}/><Profile label="이메일" value={user.email}/><Profile label="주민번호" value={user.idnum1 ? `${user.idnum1}-${user.idnum2}******` : '-'}/></dl>
        <div className="document-actions">{myInfo ? <button className="primary-button" onClick={() => navigate(`${BASE}/login/changepw.do`)}>비밀번호 변경</button> : <><button className="text-button danger" onClick={remove}><Icon name="trash"/> 삭제</button><button className="primary-button" onClick={() => navigate(`${BASE}/user/modify.do?usernum=${user.usernum}`)}><Icon name="edit"/> 수정</button></>}</div>
      </article>
    </>
  )
}

export function UserForm({ route, navigate, csrf, edit = false }) {
  const usernum = edit ? new URLSearchParams(route.search).get('usernum') : null
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getJson(edit ? `/api/users/${usernum}` : '/api/users/next-number')
      .then((data) => setUser(edit ? data : { usernum: data.usernum, authority: false, userStatus: '재직' }))
      .catch((reason) => setError(reason.message))
  }, [edit, usernum])

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const body = Object.fromEntries(form.entries())
    body.authority = body.authority === 'true'
    setSaving(true)
    setError('')
    try {
      const result = await sendJson(edit ? `/api/users/${usernum}` : '/api/users', edit ? 'PUT' : 'POST', body, csrf)
      navigate(`${BASE}/user/view.do?usernum=${result.usernum}`)
    } catch (reason) {
      setError(reason.message)
    } finally {
      setSaving(false)
    }
  }

  if (error && !user) return <PageState message={error}/>
  if (!user) return <PageState message="사원 등록 화면을 준비하고 있습니다."/>
  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">EMPLOYEE FORM</p><h1>{edit ? '사원 정보 수정' : '사원 등록'}</h1><p className="muted">업무에 필요한 사원 정보를 정확하게 입력해 주세요.</p></div></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <form className="surface workspace-form user-form" onSubmit={submit}>
        <div className="form-grid three"><label>사원명<input name="name" defaultValue={user.name || ''} autoFocus required/></label><label>사원번호<input name="usernum" defaultValue={user.usernum} readOnly={edit} required/></label><label>{edit ? '새 비밀번호 (변경 시 입력)' : '초기 비밀번호'}<input type="password" name="userpw" autoComplete="new-password" required={!edit}/></label></div>
        <div className="form-grid three"><label>주민번호 앞자리<input name="idnum1" defaultValue={user.idnum1 || ''} inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required/></label><label>주민번호 뒷자리 첫 숫자<input name="idnum2" defaultValue={user.idnum2 || ''} inputMode="numeric" pattern="[0-9]" maxLength="1" required/></label><label>입사일자<input type="date" name="joindate" defaultValue={user.joindate?.slice(0, 10) || ''}/></label></div>
        <div className="form-grid three"><label>전화번호<input type="tel" name="phonenum" defaultValue={user.phonenum || ''} placeholder="010-0000-0000" required/></label><label>내선번호<input type="tel" name="officenum" defaultValue={user.officenum || ''} placeholder="02-000-0000" required/></label><label>이메일<input type="email" name="email" defaultValue={user.email || ''} required/></label></div>
        <div className="form-grid three"><label>부서<select name="team" defaultValue={user.team || ''} required><option value="" disabled>선택해 주세요</option>{TEAMS.map((item) => <option key={item}>{item}</option>)}</select></label><label>직급<select name="level" defaultValue={user.level || ''} required><option value="" disabled>선택해 주세요</option>{LEVELS.map((item) => <option key={item}>{item}</option>)}</select></label><label>근무상태<select name="userStatus" defaultValue={user.userStatus || '재직'} required>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label></div>
        <fieldset className="authority-picker"><legend>시스템 권한</legend><label><input type="radio" name="authority" value="false" defaultChecked={!user.authority}/> 일반 사원</label><label><input type="radio" name="authority" value="true" defaultChecked={user.authority}/> 관리자</label></fieldset>
        <div className="form-actions"><button type="button" className="secondary-button" onClick={() => navigate(edit ? `${BASE}/user/view.do?usernum=${usernum}` : `${BASE}/user/list.do`)}>취소</button><button className="primary-button" disabled={saving}>{saving ? '저장 중…' : edit ? '수정 완료' : '등록 완료'}</button></div>
      </form>
    </>
  )
}

export function PasswordChange({ navigate, csrf }) {
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event) {
    event.preventDefault()
    const body = Object.fromEntries(new FormData(event.currentTarget).entries())
    if (body.newPassword !== body.confirmPassword) return setError('새 비밀번호 확인이 일치하지 않습니다.')
    setSaving(true)
    setError('')
    try {
      const result = await sendJson('/api/users/me/password', 'POST', body, csrf)
      setSuccess(result.message)
      event.currentTarget.reset()
    } catch (reason) {
      setError(reason.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">SECURITY</p><h1>비밀번호 변경</h1><p className="muted">현재 비밀번호를 확인한 뒤 새 비밀번호를 설정하세요.</p></div></section>
      {error && <div className="alert" role="alert">{error}</div>}{success && <div className="success-alert" role="status">{success}</div>}
      <form className="surface workspace-form password-form" onSubmit={submit}><label className="form-field">현재 비밀번호<input type="password" name="currentPassword" autoComplete="current-password" autoFocus required/></label><label className="form-field">새 비밀번호<input type="password" name="newPassword" autoComplete="new-password" required/></label><label className="form-field">새 비밀번호 확인<input type="password" name="confirmPassword" autoComplete="new-password" required/></label><div className="form-actions"><button type="button" className="secondary-button" onClick={() => navigate(`${BASE}/main.do`)}>취소</button><button className="primary-button" disabled={saving}>{saving ? '변경 중…' : '비밀번호 변경'}</button></div></form>
    </>
  )
}

function Profile({ label, value }) {
  return <div><dt>{label}</dt><dd>{value || '-'}</dd></div>
}
