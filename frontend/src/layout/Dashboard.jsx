import { useState } from 'react'
import { BASE } from '../api'
import { EmptyState, PageState } from '../ui'

export function Dashboard({ dashboard, navigate }) {
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
      <section className="legacy-main-heading"><h1>EZEN</h1></section>
      <section className="legacy-status-panel" aria-label="오늘의 결재 현황">
        <strong>오늘의 결재 현황 &gt;</strong>
        <div className="stat-grid">
          {stats.map(([label, value, tone, mode]) => <button key={label} className={`stat-card ${tone}`} onClick={() => navigate(mode ? `${BASE}/approval/list.do?mode=${mode}` : `${BASE}/approval/recv.do`)}><span>{label}</span><strong>{value}<small>건</small></strong></button>)}
        </div>
      </section>
      <section className="legacy-dashboard-grid">
        <Calendar />
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

function Calendar() {
  const [cursor, setCursor] = useState(() => new Date())
  const year = cursor.getFullYear()
  const month = cursor.getMonth()
  const today = new Date()
  const blanks = Array.from({ length: new Date(year, month, 1).getDay() })
  const days = Array.from({ length: new Date(year, month + 1, 0).getDate() }, (_, index) => index + 1)
  const move = (amount) => setCursor(new Date(year, month + amount, 1))

  return (
    <article className="surface legacy-calendar">
      <header><button onClick={() => move(-1)} aria-label="이전 달">&lt;</button><h2>{year}년 {month + 1}월</h2><button onClick={() => move(1)} aria-label="다음 달">&gt;</button></header>
      <div className="calendar-weekdays">{['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <span key={day}>{day}</span>)}</div>
      <div className="calendar-days">{blanks.map((_, index) => <span key={`blank-${index}`} />)}{days.map((day) => <span key={day} className={year === today.getFullYear() && month === today.getMonth() && day === today.getDate() ? 'today' : ''}>{day}</span>)}</div>
    </article>
  )
}
