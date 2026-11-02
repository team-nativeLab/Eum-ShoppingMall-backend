package com.example.shopping.global.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.shopping.global.mail.EmailSender;
import com.example.shopping.global.security.RequestIdFilter;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 담당자가 봐야 할 상황을 메일(ALERT_EMAILS)로 알린다.
 * 결제 처리나 정기 작업이 메일 서버를 기다리지 않도록 add는 메모리에 쌓기만 하고, 1분마다 한 통으로 묶어 보낸다.
 * 같은 키는 repeatAfter가 지나기 전까지 다시 쌓지 않는다. 1분마다 도는 보정 작업이 같은 주문을 계속 올려도 메일이 쏟아지지 않고,
 * 해결되지 않은 문제는 repeatAfter마다 다시 알린다. 쌓아 둔 내용은 서버별 메모리에 있으므로 서버마다 따로 보낸다.
 */
@Slf4j
@Component
public class OperatorAlerts implements DisposableBean {

	private static final int MAX_PENDING = 100;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final EmailSender emailSender;
	private final MeterRegistry meterRegistry;
	private final List<String> recipients;
	private final String serviceName;
	private final Clock clock;
	private final Map<String, Instant> quietUntil = new HashMap<>();
	private final List<String> pending = new ArrayList<>();
	private int dropped;

	@Autowired
	public OperatorAlerts(EmailSender emailSender, MeterRegistry meterRegistry,
			@Value("${app.alert.emails:}") List<String> recipients,
			@Value("${app.mail.service-name:}") String serviceName) {
		this(emailSender, meterRegistry, recipients, serviceName, Clock.systemDefaultZone());
	}

	OperatorAlerts(EmailSender emailSender, MeterRegistry meterRegistry, List<String> recipients, String serviceName,
			Clock clock) {
		this.emailSender = emailSender;
		this.meterRegistry = meterRegistry;
		this.recipients = recipients.stream().map(String::trim).filter(StringUtils::hasText).toList();
		this.serviceName = serviceName;
		this.clock = clock;
		if (this.recipients.isEmpty()) {
			log.warn("담당자 알림 수신자(ALERT_EMAILS)가 없어 확인이 필요한 상황을 로그와 지표로만 남깁니다.");
		}
	}

	/**
	 * @param key         같은 문제를 가리키는 키. 같은 키는 repeatAfter 동안 한 번만 알린다.
	 * @param message     메일에 넣을 내용. 이메일·연락처·토큰을 넣지 않는다.
	 * @param repeatAfter 해결되지 않았을 때 다시 알릴 간격
	 */
	public synchronized void add(String key, String message, Duration repeatAfter) {
		if (recipients.isEmpty()) {
			return;
		}
		Instant now = clock.instant();
		Instant until = quietUntil.get(key);
		if (until != null && now.isBefore(until)) {
			return;
		}
		if (pending.size() >= MAX_PENDING) {
			dropped++;
			return;
		}
		quietUntil.put(key, now.plus(repeatAfter));
		String requestId = MDC.get(RequestIdFilter.MDC_KEY);
		pending.add("- " + LocalDateTime.now(clock).format(TIME_FORMAT) + " " + message
				+ (requestId == null ? "" : " (requestId=" + requestId + ")"));
	}

	@Scheduled(fixedDelay = 60_000)
	public void sendPending() {
		List<String> alerts;
		int droppedCount;
		synchronized (this) {
			Instant now = clock.instant();
			quietUntil.values().removeIf(until -> !now.isBefore(until));
			if (pending.isEmpty()) {
				return;
			}
			alerts = List.copyOf(pending);
			droppedCount = dropped;
			pending.clear();
			dropped = 0;
		}
		String subject = "[" + serviceName + "] 담당자 확인 필요 " + (alerts.size() + droppedCount) + "건";
		String text = text(alerts, droppedCount);
		int delivered = 0;
		for (String recipient : recipients) {
			try {
				emailSender.send(recipient, subject, text);
				delivered++;
			} catch (EmailSender.EmailSendException e) {
				meterRegistry.counter("operator.alert.delivery.failures").increment();
				log.error("담당자 알림 메일을 보내지 못했습니다.", e);
			}
		}
		if (delivered == 0) {
			// 메일 서버 장애로 아무도 받지 못했으므로 다음 차례에 다시 보낸다.
			putBack(alerts, droppedCount);
		}
	}

	/** 서버가 내려가기 전에 쌓인 알림을 보낸다. */
	@Override
	public void destroy() {
		sendPending();
	}

	private String text(List<String> alerts, int droppedCount) {
		String text = "담당자 확인이 필요한 상황입니다. 자세한 내용은 서버 로그에서 찾습니다.\n\n" + String.join("\n", alerts) + "\n";
		return droppedCount == 0 ? text : text + "\n이 밖에 " + droppedCount + "건은 메일에 담지 못했습니다. 서버 로그를 확인해 주세요.\n";
	}

	private synchronized void putBack(List<String> alerts, int droppedCount) {
		List<String> merged = new ArrayList<>(alerts);
		merged.addAll(pending);
		int kept = Math.min(merged.size(), MAX_PENDING);
		dropped += droppedCount + merged.size() - kept;
		pending.clear();
		pending.addAll(merged.subList(0, kept));
	}
}
