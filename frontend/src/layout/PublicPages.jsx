import { useState } from 'react'
import { BASE } from '../api'
import { Icon } from '../ui'

export function LandingPage({ authenticated }) {
  const workspaceUrl = authenticated ? `${BASE}/main.do` : `${BASE}/login/login.do`
  return (
    <main className="landing-page">
      <header className="landing-header">
        <a className="landing-logo" href={`${BASE}/`}><span>EW</span> EZEN WORKS</a>
        <nav aria-label="랜딩 페이지 메뉴"><a href="#features">주요 기능</a><a href="#workflow">업무 흐름</a><a className="landing-login" href={workspaceUrl}>{authenticated ? '업무 공간' : '로그인'}</a></nav>
      </header>

      <section className="landing-hero">
        <div className="landing-copy">
          <p className="landing-eyebrow">SMART WORKSPACE FOR EVERY TEAM</p>
          <h1>결재부터 공지, AI 업무 지원까지<br /> <em>한곳에서 가볍게.</em></h1>
          <p>기존 업무 흐름은 그대로 유지하면서, 더 빠르고 투명하게 협업할 수 있는 사내 업무 공간입니다.</p>
          <div className="landing-actions"><a className="landing-primary" href={workspaceUrl}>{authenticated ? '업무 공간 열기' : '로그인하고 시작하기'} <span>→</span></a><a className="landing-secondary" href="#features">기능 살펴보기</a></div>
          <div className="landing-stack"><span>Spring Boot</span><span>React</span><span>MySQL</span><span>Local LLM</span></div>
        </div>

        <div className="landing-preview" aria-label="업무 화면 미리보기">
          <div className="preview-top"><span><b>EW</b> 업무 대시보드</span><i>김사원</i></div>
          <div className="preview-status"><div className="blue"><span>결재 대기</span><strong>3</strong></div><div className="yellow"><span>진행 중</span><strong>2</strong></div><div className="green"><span>승인 완료</span><strong>12</strong></div></div>
          <div className="preview-body">
            <div className="preview-document"><span className="preview-label">최근 결재</span><strong>신규 프로젝트 장비 구매 품의</strong><div className="preview-line"><span className="done">작성</span><i/><span className="done">검토</span><i/><span>승인</span></div></div>
            <div className="preview-ai"><Icon name="chat"/><div><small>AI 업무 도우미</small><strong>결재 내용을 요약했어요.</strong></div></div>
          </div>
        </div>
      </section>

      <section className="landing-features" id="features">
        <div className="landing-section-heading"><p>CORE FEATURES</p><h2>업무에 필요한 기능만<br />명확하게 담았습니다.</h2></div>
        <div className="feature-grid">
          <article><span><Icon name="document"/></span><h3>전자결재</h3><p>문서 작성부터 결재선 지정, 승인과 반려까지 모든 상태를 한눈에 확인합니다.</p></article>
          <article><span><Icon name="users"/></span><h3>권한 관리</h3><p>작성자, 결재자, 관리자 역할에 맞춰 문서와 사원 정보 접근을 안전하게 제어합니다.</p></article>
          <article><span><Icon name="bell"/></span><h3>공지와 사원관리</h3><p>사내 소식과 구성원 정보를 한 업무 공간에서 빠르게 찾고 관리합니다.</p></article>
          <article><span><Icon name="chat"/></span><h3>로컬 AI 도우미</h3><p>사내 데이터가 외부로 나가지 않도록 로컬 LLM 기반 검색과 요약을 준비합니다.</p></article>
        </div>
      </section>

      <section className="landing-workflow" id="workflow">
        <div><p className="landing-eyebrow">SIMPLE WORKFLOW</p><h2>복잡한 결재도<br />세 단계면 충분합니다.</h2></div>
        <ol><li><b>01</b><div><strong>문서 작성</strong><span>필요한 내용을 입력하고 결재선을 지정합니다.</span></div></li><li><b>02</b><div><strong>검토와 결재</strong><span>결재자가 내용을 확인하고 승인 또는 반려합니다.</span></div></li><li><b>03</b><div><strong>결과 확인</strong><span>처리 상태와 의견을 실시간으로 확인합니다.</span></div></li></ol>
      </section>

      <section className="landing-cta"><div><p>EZEN WORKS</p><h2>더 맑고 가벼운 업무 흐름을<br />지금 시작해 보세요.</h2></div><a href={workspaceUrl}>{authenticated ? '업무 공간 열기' : '로그인하기'} →</a></section>
      <footer className="landing-footer"><strong>EZEN WORKS</strong><span>기업 전자결재 웹 서비스</span><small>© 2026 EZEN Company</small></footer>
    </main>
  )
}

export function LoginPage({ csrf }) {
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
      const response = await fetch(`${BASE}/login/login.do`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body })
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
      <a className="login-home" href={`${BASE}/`}><span>EW</span> EZEN WORKS</a>
      <section className="login-brand" aria-label="서비스 소개">
        <div className="login-copy">
          <p className="eyebrow">SMART WORKSPACE</p>
          <h1>더 맑고 가벼운<br/><em>업무의 시작.</em></h1>
          <p>전자결재부터 사내 메신저와 AI 업무 지원까지, 필요한 흐름을 한곳에서 이어갑니다.</p>
          <div className="login-features">
            <span><Icon name="document"/> 전자결재</span>
            <span><Icon name="users"/> 사내 메신저</span>
            <span><Icon name="chat"/> AI 업무 지원</span>
          </div>
        </div>
        <div className="login-preview" aria-hidden="true">
          <span>오늘의 결재</span><strong>3<small>건</small></strong><i>확인할 업무를 한눈에</i>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <p className="eyebrow">WELCOME BACK</p>
          <h2>업무 공간에 로그인</h2>
          <p className="login-description">사원번호와 비밀번호를 입력해 주세요.</p>
          <form onSubmit={submit} className="login-form">
            <label>사원번호<input name="usernum" autoComplete="username" autoFocus required placeholder="사원번호 입력" /></label>
            <label>비밀번호<input type="password" name="userpw" autoComplete="current-password" required placeholder="비밀번호 입력" /></label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="primary-button login-button" disabled={loading}>{loading ? '로그인 중…' : '로그인'}</button>
          </form>
          <p className="login-help">계정 문의는 사내 관리자에게 요청해 주세요.</p>
          <a className="login-back" href={`${BASE}/`}>← 서비스 소개로 돌아가기</a>
        </div>
      </section>
    </main>
  )
}
