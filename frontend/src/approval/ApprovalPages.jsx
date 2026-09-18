import { useEffect, useMemo, useState } from 'react'
import { BASE, getJson } from '../api'
import { CardSkeleton, EmptyState, Icon, PageState, initials } from '../ui'

export function ApprovalList({ route, navigate }) {
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
      <section className="page-heading"><div><p className="page-kicker">MY APPROVAL</p><h1>내 결재 관리</h1><p className="muted">작성한 문서의 처리 상태를 한눈에 확인하세요.</p></div><a className="primary-button" href={`${BASE}/approval/write.do`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/write.do`) }}><Icon name="plus"/> 새 결재 작성</a></section>
      <section className="surface list-surface">
        <div className="list-title"><div><h2>{titles[mode] || '전체 문서'}</h2><span>{data ? `총 ${data.totalCount}건` : '불러오는 중'}</span></div></div>
        <form className="filter-bar" onSubmit={applyFilters}>
          <label><span className="sr-only">문서 구분</span><select value={kind} onChange={(event) => setKind(event.target.value)}><option value="">모든 문서</option><option>품의서</option><option>기안서</option><option>연차신청서</option></select></label>
          <label><span className="sr-only">결재 상태</span><select value={mode} onChange={(event) => setMode(event.target.value)}><option value="0">모든 상태</option><option value="1">대기중</option><option value="2">반려</option><option value="3">진행중</option><option value="4">승인</option></select></label>
          <label className="search-field"><Icon name="search" size={18}/><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="문서 제목 검색"/></label><button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton/> : data.items.length ? <div className="approval-cards">{data.items.map((item) => <ApprovalCard key={item.approvalNo} item={item} navigate={navigate}/>)}</div> : <EmptyState title="조건에 맞는 결재 문서가 없습니다." description="검색 조건을 바꾸거나 새 문서를 작성해 보세요."/>}
        <Pagination data={data} apply={applyFilters}/>
      </section>
    </>
  )
}

export function ApprovalCollection({ type, route, navigate }) {
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
          <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label={type === 'received' ? '결재 상태' : '부서'}><option value="">{type === 'received' ? '모든 상태' : '모든 부서'}</option>{(type === 'received' ? ['대기중', '진행중', '승인', '반려'] : ['개발', '디자인', '경영지원']).map((value) => <option key={value}>{value}</option>)}</select>
          <label className="search-field"><Icon name="search" size={18}/><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="문서 제목 검색"/></label><button className="secondary-button">검색</button>
        </form>
        {error && <div className="alert" role="alert">{error}</div>}
        {!data ? <CardSkeleton/> : data.items.length ? <div className="approval-cards">{data.items.map((item) => <ApprovalCard key={item.approvalNo} item={item} navigate={navigate}/>)}</div> : <EmptyState title="조건에 맞는 결재 문서가 없습니다."/>}
        <Pagination data={data} apply={applyFilters}/>
      </section>
    </>
  )
}

export function ApprovalForm({ session, route, edit = false }) {
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

  if (error && !options) return <PageState message={error}/>
  if (!options || (edit && !detail)) return <PageState message="결재 작성 화면을 준비하고 있습니다."/>
  const item = detail?.item
  return (
    <>
      <section className="page-heading"><div><p className="page-kicker">APPROVAL FORM</p><h1>{edit ? '결재 문서 수정' : '결재 작성'}</h1><p className="muted">문서를 작성하고 결재선을 지정하세요.</p></div></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <form className="surface workspace-form" action={`${BASE}/approval/${edit ? 'modify.do' : 'write.do'}`} method="post" encType="multipart/form-data" onSubmit={validate}>
        <input type="hidden" name={session.csrf.parameterName} value={session.csrf.token}/>{edit && <input type="hidden" name="approval_no" value={approvalNo}/>} {targets.map((target) => <input key={target} type="hidden" name="approval_target" value={target}/>)}
        <div className="form-grid three"><label>문서 구분<select name="kind" defaultValue={item?.kind || ''} required><option value="" disabled>선택해 주세요</option><option>연차신청서</option><option>품의서</option><option>기안서</option></select></label><label>품의 번호<input name="approval_code" defaultValue={item?.code || ''} placeholder="예: EW-2026-001"/></label><label>작성일<input name="writedate" value={options.date} readOnly/></label></div>
        <div className="form-drafter"><span className="avatar">{initials(options.drafter.name)}</span><div><small>기안자</small><strong>{options.drafter.name}</strong><span>{options.drafter.team} · {options.drafter.level}</span></div></div>
        <label className="form-field">제목<input name="approval_title" defaultValue={item?.title || ''} placeholder="제목을 입력해 주세요" required/></label>
        <label className="form-field">내용<textarea name="approval_content" defaultValue={item?.content || ''} placeholder="내용을 입력해 주세요" required/></label>
        <fieldset className="approver-picker"><legend>결재선</legend><div>{options.approvers.map((person) => <label key={person.usernum} className={targets.includes(person.usernum) ? 'selected' : ''}><input type="checkbox" checked={targets.includes(person.usernum)} onChange={() => toggleTarget(person.usernum)}/><span className="avatar small">{initials(person.name)}</span><span><strong>{person.name}</strong><small>{person.team} · {person.level}</small></span></label>)}</div></fieldset>
        <label className="form-field">첨부파일<input type="file" name="attach"/></label>{edit && detail.files.length > 0 && <p className="muted">현재 파일: {detail.files.map((file) => file.name).join(', ')}</p>}
        <div className="form-actions"><a className="secondary-button" href={`${BASE}/approval/list.do?mode=0`}>취소</a><button className="primary-button">{edit ? '수정 완료' : '등록 완료'}</button></div>
      </form>
    </>
  )
}

export function ApprovalDetail({ route, csrf, navigate }) {
  const id = new URLSearchParams(route.search).get('approval_no')
  const [data, setData] = useState(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const load = () => getJson(`/api/approvals/${id}`).then(setData).catch((reason) => setError(reason.message))

  useEffect(() => { load() }, [id])

  async function submitAction(status) {
    if (status === '반려' && !comment.trim()) return setError('반려 의견을 입력해 주세요.')
    const body = new URLSearchParams({ approval_no: id, approval_status: status, comment, [csrf.parameterName]: csrf.token })
    setSaving(true)
    setError('')
    const response = await fetch(`${BASE}/approval/updateComment.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
    if (!response.ok) setError((await response.text()) || '처리하지 못했습니다.')
    else { setComment(''); await load() }
    setSaving(false)
  }

  async function remove() {
    if (!window.confirm('문서를 삭제하시겠습니까?')) return
    const body = new URLSearchParams({ approval_no: id, [csrf.parameterName]: csrf.token })
    const response = await fetch(`${BASE}/approval/delete.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
    if (response.ok) navigate(`${BASE}/approval/list.do?mode=0`)
    else setError('문서를 삭제하지 못했습니다.')
  }

  if (error && !data) return <PageState message={error}/>
  if (!data) return <PageState message="결재 문서를 불러오는 중입니다."/>
  const item = data.item
  return (
    <>
      <section className="page-heading compact"><div><button className="text-button" onClick={() => navigate(`${BASE}/approval/list.do?mode=0`)}>← 목록으로</button><p className="page-kicker">APPROVAL DETAIL</p><h1>{item.title}</h1></div><StatusBadge status={item.status}/></section>
      {error && <div className="alert" role="alert">{error}</div>}
      <article className="surface document-surface">
        <div className="document-header"><div className="document-number"><span>{item.kind}</span><strong>{item.code || `DOC-${item.approvalNo}`}</strong></div><div className="drafter"><span className="avatar">{initials(item.drafter?.name)}</span><div><small>기안자</small><strong>{item.drafter?.name}</strong><span>{item.drafter?.team} · {item.drafter?.level}</span></div></div></div>
        <dl className="document-info"><div><dt>작성일</dt><dd>{item.writeDate}</dd></div><div><dt>문서 번호</dt><dd>#{item.approvalNo}</dd></div><div><dt>문서 상태</dt><dd>{item.status}</dd></div></dl>
        <section className="document-content"><h2>기안 내용</h2><div>{item.content || '내용이 없습니다.'}</div></section>
        <section className="approval-route"><div className="section-heading"><div><p className="page-kicker">APPROVAL LINE</p><h2>결재선</h2></div></div><div className="approval-line-list">{data.lines.map((line, index) => <div className="approval-person" key={`${line.target}-${index}`}><span className="line-order">{index + 1}</span><span className="avatar">{initials(line.approver?.name)}</span><div><strong>{line.approver?.name}</strong><small>{line.approver?.team} · {line.approver?.level}</small>{line.comment && <p>“{line.comment}”</p>}</div><div className="line-result"><StatusBadge status={line.status === '대기' ? '대기중' : line.status}/><time>{line.date || '처리 전'}</time></div></div>)}</div></section>
        <section className="attachments"><h2>첨부파일</h2>{data.files.length ? data.files.map((file) => <a key={file.name} href={file.downloadUrl}><Icon name="download"/> {file.name}</a>) : <p className="muted">첨부파일이 없습니다.</p>}</section>
        {data.canApprove && <section className="decision-box"><label>결재 의견<textarea value={comment} onChange={(event) => setComment(event.target.value)} placeholder="승인 또는 반려 의견을 입력하세요"/></label><div><button className="secondary-button danger" onClick={() => submitAction('반려')} disabled={saving}>반려</button><button className="primary-button" onClick={() => submitAction('승인')} disabled={saving}><Icon name="check"/> 승인</button></div></section>}
        {(data.canEdit || data.canDelete) && <div className="document-actions">{data.canDelete && <button className="text-button danger" onClick={remove}><Icon name="trash"/> 삭제</button>}{data.canEdit && <a className="secondary-button" href={`${BASE}/approval/modify.do?approval_no=${item.approvalNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/modify.do?approval_no=${item.approvalNo}`) }}><Icon name="edit"/> 수정</a>}</div>}
      </article>
    </>
  )
}

function ApprovalCard({ item, navigate }) {
  return <a className="approval-card" href={`${BASE}/approval/view.do?approval_no=${item.approvalNo}`} onClick={(event) => { event.preventDefault(); navigate(`${BASE}/approval/view.do?approval_no=${item.approvalNo}`) }}><div className="doc-icon"><Icon name="document"/></div><div className="approval-main"><div className="card-meta"><span>{item.kind}</span><time>{item.writeDate}</time></div><h3>{item.title}</h3><p>{item.drafter?.name} · {item.drafter?.team} {item.drafter?.level}</p></div><StatusBadge status={item.status}/><Icon name="arrow"/></a>
}

function StatusBadge({ status }) {
  const tone = { 승인: 'approved', 진행중: 'progress', 반려: 'rejected', 대기중: 'pending' }[status] || 'pending'
  return <span className={`status-badge ${tone}`}><i/>{status}</span>
}

function Pagination({ data, apply }) {
  return data?.totalPages > 1 && <nav className="pagination" aria-label="페이지 이동">{Array.from({ length: data.totalPages }, (_, index) => index + 1).map((page) => <button key={page} className={page === data.page ? 'active' : ''} onClick={(event) => apply(event, page)}>{page}</button>)}</nav>
}
