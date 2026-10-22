package com.erp.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erp.mapper.UserMapper;
import com.erp.vo.userVO;

@Service
public class SessionRevocationService implements HttpSessionListener {

	private static final String REGISTRATION = SessionRevocationService.class.getName() + ".registration";
	private final UserMapper userMapper;
	// Servlet sessions are local to this single application instance; state changes are serialized in MySQL.
	private final ConcurrentHashMap<HttpSession, Registration> sessions = new ConcurrentHashMap<>();

	public SessionRevocationService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Transactional
	public userVO register(HttpSession session, Authentication authentication) {
		List<userVO> locked = userMapper.selectUsersForUpdate(List.of(authentication.getName()));
		userVO user = locked.isEmpty() ? null : userMapper.selectUserForUpdate(authentication.getName());
		boolean admin = authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
		if (user == null || !"재직".equals(user.getUser_status()) || user.isAuthority() != admin) {
			session.invalidate();
			throw new CredentialsExpiredException("사원 상태가 변경되었습니다. 다시 로그인해 주세요.");
		}
		synchronized (session) {
			Registration registration = new Registration(user.getUsernum(), session);
			session.setAttribute("loginUser", user);
			session.setAttribute(REGISTRATION, registration);
			sessions.put(session, registration);
		}
		return user;
	}

	public void revokeAfterCommit(String usernum) {
		// Capture while the caller holds the user row lock. A later login with the new role must survive.
		// ponytail: revocation scans live sessions on this instance; add a user index only if volume requires it.
		List<Registration> revoked = sessions.values().stream()
				.filter(registration -> usernum.equals(registration.usernum)).toList();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (Registration registration : revoked) {
					HttpSession session = registration.session;
					synchronized (session) {
						try {
							// Re-login can reuse a session object after changing its ID or account.
							if (session.getAttribute(REGISTRATION) == registration) {
								// The container also closes authenticated WebSockets for this HTTP session.
								session.invalidate();
							}
						} catch (IllegalStateException alreadyInvalidated) {
							// Logout or expiry may have invalidated the session first.
						} finally {
							sessions.remove(session, registration);
						}
					}
				}
			}
		});
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		sessions.remove(event.getSession());
	}

	private static final class Registration {
		private final String usernum;
		private final HttpSession session;

		private Registration(String usernum, HttpSession session) {
			this.usernum = usernum;
			this.session = session;
		}
	}
}
