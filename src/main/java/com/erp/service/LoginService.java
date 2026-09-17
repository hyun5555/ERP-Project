package com.erp.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erp.mapper.LoginMapper;
import com.erp.vo.userVO;

@Service
public class LoginService implements UserDetailsService, UserDetailsPasswordService {

	private final LoginMapper loginMapper;
	private final PasswordEncoder passwordEncoder;

	public LoginService(LoginMapper loginMapper, PasswordEncoder passwordEncoder) {
		this.loginMapper = loginMapper;
		this.passwordEncoder = passwordEncoder;
	}

	public userVO findByUsernum(String usernum) {
		return loginMapper.findByUsernum(usernum);
	}

	@Override
	public UserDetails loadUserByUsername(String usernum) {
		userVO user = findByUsernum(usernum);
		if (user == null) {
			throw new UsernameNotFoundException(usernum);
		}

		return User.withUsername(user.getUsernum())
				.password(user.getUserpw())
				.roles(user.isAuthority() ? "ADMIN" : "USER")
				.disabled(!"재직".equals(user.getUser_status()))
				.build();
	}

	@Override
	@Transactional
	public UserDetails updatePassword(UserDetails user, String newPassword) {
		loginMapper.updatePassword(user.getUsername(), newPassword);
		return User.withUserDetails(user).password(newPassword).build();
	}

	@Transactional
	public boolean changePassword(String usernum, String oldPassword, String newPassword) {
		userVO user = findByUsernum(usernum);
		if (user == null || newPassword == null || newPassword.isBlank()
				|| !passwordEncoder.matches(oldPassword, user.getUserpw())) {
			return false;
		}

		return loginMapper.changePassword(usernum, passwordEncoder.encode(newPassword)) == 1;
	}
}
