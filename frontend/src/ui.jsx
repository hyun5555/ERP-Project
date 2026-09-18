export function initials(name = '') {
  return name.trim().slice(-2) || 'EW'
}

export function Icon({ name, size = 20 }) {
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
    plus: <path d="M12 5v14M5 12h14"/>,
    check: <path d="m5 12 4 4L19 6"/>,
    download: <><path d="M12 3v12M7 10l5 5 5-5"/><path d="M5 21h14"/></>,
    trash: <><path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14"/></>
  }
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

export function PageState({ message }) {
  return <div className="page-state"><span className="loader"/><p>{message}</p></div>
}

export function EmptyState({ title, description }) {
  return <div className="empty-state"><span><Icon name="document" /></span><strong>{title}</strong>{description && <p>{description}</p>}</div>
}

export function CardSkeleton() {
  return <div className="approval-cards" aria-label="목록 로딩 중">{[1, 2, 3].map((item) => <div className="approval-card skeleton" key={item}><i/><div><b/><span/></div></div>)}</div>
}
