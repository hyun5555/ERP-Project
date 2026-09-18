import { useEffect, useMemo, useState } from 'react'
import { BASE, getJson } from '../api'
import { CardSkeleton, EmptyState, Icon, PageState } from '../ui'

export function NoticeList({ session, route, navigate }) {
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
      <section className="page-heading"><div><p className="page-kicker">NOTICE</p><h1>공지사항</h1><p className="muted">회사 소식과 주요 안내를 확인하세요.</p></div>{session.user.authority && <a className="primary-button" href={`${BASE}/notice/write.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/write.do`) }}><Icon name="plus"/> 공지 등록</a>}</section>
      <section className="surface list-surface">
        <div className="list-title"><div><h2>공지 목록</h2><span>{data ? `총 ${data.totalCount}건` : '불러오는 중'}</span></div></div>
        <form className="filter-bar notice-filter" onSubmit={applyFilters}><select value={team} onChange={(event) => setTeam(event.target.value)} aria-label="공지 대상"><option value="">모든 부서</option><option value="100">개발</option><option value="200">디자인</option><option value="300">경영지원</option></select><label className="search-field"><Icon name="search" size={18}/><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="공지 제목 검색"/></label><button className="secondary-button">검색</button></form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton/> : data.items.length ? <div className="notice-cards">{data.items.map((notice) => <a key={notice.noticeNo} href={`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/notice/view.do?notice_no=${notice.noticeNo}`) }}><span className={`notice-dot ${notice.important ? 'important' : ''}`}/><div><small>{notice.important ? '중요 공지' : '공지사항'}</small><strong>{notice.title}</strong><span>{notice.writer || '관리자'}</span></div><time>{notice.date}</time><Icon name="arrow"/></a>)}</div> : <EmptyState title="등록된 공지사항이 없습니다."/>}
        {data?.totalPages > 1 && <nav className="pagination">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => applyFilters(event, page)}>{page}</button>)}</nav>}
      </section>
    </>
  )
}

export function NoticeDetail({ route, csrf, navigate }) {
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

  if (error && !notice) return <PageState message={error}/>
  if (!notice) return <PageState message="공지사항을 불러오는 중입니다."/>
  return (
    <>
      <section className="page-heading compact"><div><button className="text-button" onClick={() => navigate(`${BASE}/notice/list.do`)}>← 목록으로</button><p className="page-kicker">NOTICE DETAIL</p><h1>{notice.title}</h1></div>{notice.important && <span className="status-badge rejected"><i/>중요</span>}</section>
      {error && <div className="alert">{error}</div>}
      <article className="surface document-surface notice-detail">
        <dl className="document-info"><div><dt>작성자</dt><dd>{notice.writer || '관리자'}</dd></div><div><dt>작성일</dt><dd>{notice.date}</dd></div><div><dt>공지 대상</dt><dd>{notice.teams.join(', ') || '전체'}</dd></div></dl>
        <section className="document-content"><h2>공지 내용</h2><div>{notice.content || '내용이 없습니다.'}</div></section>
        <section className="attachments"><h2>첨부파일</h2>{notice.downloadUrl ? <a href={notice.downloadUrl}><Icon name="download"/> {notice.fileName}</a> : <p className="muted">첨부파일이 없습니다.</p>}</section>
        {notice.canDelete && <div className="document-actions"><button className="text-button danger" onClick={remove}><Icon name="trash"/> 삭제</button></div>}
      </article>
    </>
  )
}

export function NoticeForm({ csrf }) {
  const [teams, setTeams] = useState([])
  const [error, setError] = useState('')
  const toggleTeam = (team) => setTeams((items) => items.includes(team) ? items.filter((item) => item !== team) : [...items, team])
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
        <input type="hidden" name={csrf.parameterName} value={csrf.token}/>{teams.map((team) => <input key={team} type="hidden" name="notice_team" value={team}/>)}
        <label className="form-field">제목<input name="notice_title" placeholder="제목을 입력해 주세요" autoFocus required/></label>
        <div className="check-row"><label><input type="checkbox" name="is_important" value="1"/> 중요 공지</label><label><input type="checkbox" name="is_main" value="1"/> 메인 화면 노출</label></div>
        <fieldset className="approver-picker"><legend>공지 대상</legend><div>{[['100', '개발'], ['200', '디자인'], ['300', '경영지원']].map(([value, label]) => <label key={value} className={teams.includes(value) ? 'selected' : ''}><input type="checkbox" checked={teams.includes(value)} onChange={() => toggleTeam(value)}/><span><strong>{label}</strong><small>부서 구성원</small></span></label>)}</div></fieldset>
        <label className="form-field">내용<textarea name="notice_content" placeholder="내용을 입력해 주세요" required/></label><label className="form-field">첨부파일<input type="file" name="fileInput"/></label>
        <div className="form-actions"><a className="secondary-button" href={`${BASE}/notice/list.do`}>취소</a><button className="primary-button">등록 완료</button></div>
      </form>
    </>
  )
}
