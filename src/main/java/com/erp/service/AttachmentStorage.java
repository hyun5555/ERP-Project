package com.erp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.erp.mapper.AttachmentMapper;

@Service
public class AttachmentStorage {

	private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);
	private final Path directory;
	private final AttachmentMapper attachments;

	public AttachmentStorage(@Value("${erp.upload-dir}") String directory, AttachmentMapper attachments) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
		this.attachments = attachments;
	}

	public StoredFile store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) return null;
		Files.createDirectories(directory);
		String name = UUID.randomUUID().toString();
		Path path = directory.resolve(name);
		try {
			file.transferTo(path);
		} catch (IOException | RuntimeException failure) {
			try { Files.deleteIfExists(path); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
		return new StoredFile(file.getOriginalFilename(), name);
	}

	public void deleteAfterCommit(Collection<String> names) {
		var savedNames = names.stream().filter(name -> name != null && !name.isBlank()).distinct().toList();
		if (savedNames.isEmpty()) return;
		// shortcut: cleanup is in-process; persist cleanup jobs if crash-time orphan recovery is required.
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() { savedNames.forEach(AttachmentStorage.this::deleteIfUnreferenced); }
			});
		} else {
			savedNames.forEach(this::deleteIfUnreferenced);
		}
	}

	public void deleteIfUnreferenced(String name) {
		if (name == null || name.isBlank()) return;
		try {
			Path path = directory.resolve(name).normalize();
			if (!directory.equals(path.getParent()) || name.contains("/") || name.contains("\\")) return;
			if (!attachments.isReferenced(name)) Files.deleteIfExists(path);
		} catch (IOException | RuntimeException failure) {
			// A cleanup failure must not make a committed DB change look rolled back to its caller.
			log.warn("첨부 정리 실패: {}", name, failure);
		}
	}

	public record StoredFile(String originalName, String storedName) {}
}
