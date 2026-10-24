package com.example.shopping.global.scheduling;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * DB lease locks keep the same maintenance job from running on multiple app instances.
 * A job's row is created on first use, so new jobs do not need a seed migration.
 */
@Component
@RequiredArgsConstructor
public class SchedulerLockService {

	private final JdbcTemplate jdbcTemplate;

	public Optional<String> tryAcquire(String jobName, Duration lockDuration) {
		String owner = UUID.randomUUID().toString();
		int updated = jdbcTemplate.update("""
				UPDATE scheduler_lock
				SET lock_until = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)), lock_owner = ?
				WHERE lock_name = ? AND lock_until <= CURRENT_TIMESTAMP(6)
				""", lockDuration.toSeconds(), owner, jobName);
		if (updated == 1) {
			return Optional.of(owner);
		}
		// The row is either held by another instance or does not exist yet; the primary key settles the race.
		try {
			jdbcTemplate.update("""
					INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
					VALUES (?, TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)), ?, 0)
					""", jobName, lockDuration.toSeconds(), owner);
			return Optional.of(owner);
		} catch (DataIntegrityViolationException alreadyExists) {
			return Optional.empty();
		}
	}

	public boolean release(String jobName, String owner) {
		return jdbcTemplate.update("""
				UPDATE scheduler_lock
				SET lock_until = CURRENT_TIMESTAMP(6), lock_owner = NULL
				WHERE lock_name = ? AND lock_owner = ?
				""", jobName, owner) == 1;
	}

	public long getCursor(String jobName, String owner) {
		Long cursor = jdbcTemplate.queryForObject("""
				SELECT last_order_id FROM scheduler_lock WHERE lock_name = ? AND lock_owner = ?
				""", Long.class, jobName, owner);
		if (cursor == null) {
			throw new IllegalStateException("Scheduler lease was lost: " + jobName);
		}
		return cursor;
	}

	public boolean advanceCursor(String jobName, String owner, long cursor) {
		return jdbcTemplate.update("""
				UPDATE scheduler_lock SET last_order_id = ?
				WHERE lock_name = ? AND lock_owner = ?
				""", cursor, jobName, owner) == 1;
	}
}
