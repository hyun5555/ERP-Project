(function () {
	const root = document.documentElement;
	const modal = document.getElementById('exampleModal');
	if (window.matchMedia('(max-width: 767px)').matches) {
		document.querySelector('.page-wrapper')?.classList.remove('toggled');
	}

	document.querySelector('[data-theme-toggle]')?.addEventListener('click', function () {
		const theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
		root.dataset.theme = theme;
		localStorage.setItem('erp-theme', theme);
		this.setAttribute('aria-label', theme === 'dark' ? '라이트 모드 전환' : '다크 모드 전환');
	});

	const chat = document.querySelector('[data-chat-panel]');
	const toggleChat = () => { if (chat) chat.hidden = !chat.hidden; };
	document.querySelector('[data-chat-toggle]')?.addEventListener('click', toggleChat);
	document.querySelector('[data-chat-close]')?.addEventListener('click', toggleChat);

	if (!modal) return;
	const openModal = () => {
		modal.classList.add('is-open');
		modal.setAttribute('aria-hidden', 'false');
		document.body.classList.add('modal-open');
	};
	document.getElementById('approval-add')?.addEventListener('click', openModal);
	modal.querySelector('.btn-close')?.addEventListener('click', closeMemberModal);
	modal.addEventListener('click', (event) => { if (event.target === modal) closeMemberModal(); });
	document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeMemberModal(); });
})();
