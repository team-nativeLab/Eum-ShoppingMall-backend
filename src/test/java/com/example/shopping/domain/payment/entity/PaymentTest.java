package com.example.shopping.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.gateway.PgVirtualAccount;

/** 입금이 끝났거나 기한이 지난 가상계좌의 번호·입금자명을 가린다 (#144). */
class PaymentTest {

	private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 8, 23, 59);

	@Test
	void 입금이_끝나면_계좌번호_끝_4자리와_입금자명_첫_글자만_남긴다() {
		Payment payment = waitingDeposit();

		payment.recordDeposit(pg(PgStatus.DONE));

		assertThat(payment.getVirtualAccountNumber()).isEqualTo("********5678");
		assertThat(payment.getVirtualAccountCustomerName()).isEqualTo("홍**");
		// 웹훅·보정이 같은 가상계좌인지 확인할 때 쓰는 값은 남는다.
		assertThat(payment.getVirtualAccountBankCode()).isEqualTo("004");
		assertThat(payment.getVirtualAccountDueAt()).isEqualTo(DUE);
		assertThat(payment.matchesWebhookSecret("webhook-secret")).isTrue();
	}

	@Test
	void 입금_기한이_지나도_가린다() {
		Payment payment = waitingDeposit();

		payment.expireVirtualAccount();

		assertThat(payment.getVirtualAccountNumber()).isEqualTo("********5678");
		assertThat(payment.getVirtualAccountCustomerName()).isEqualTo("홍**");
	}

	@Test
	void 입금이_취소돼_다시_입금_대기가_되면_PG가_알려_준_계좌_정보로_되살린다() {
		Payment payment = waitingDeposit();
		payment.recordDeposit(pg(PgStatus.DONE));

		payment.revertDeposit(pg(PgStatus.WAITING_FOR_DEPOSIT));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
		assertThat(payment.getVirtualAccountNumber()).isEqualTo("110123455678");
		assertThat(payment.getVirtualAccountCustomerName()).isEqualTo("홍길동");
	}

	@Test
	void 가릴_값이_짧으면_모두_가린다() {
		assertThat(Payment.keepLast("1234", 4)).isEqualTo("****");
		assertThat(Payment.keepFirst("홍", 1)).isEqualTo("*");
		assertThat(Payment.keepLast(null, 4)).isNull();
	}

	private static Payment waitingDeposit() {
		return Payment.of(1L, pg(PgStatus.WAITING_FOR_DEPOSIT), PaymentStatus.WAITING_DEPOSIT);
	}

	private static PgPayment pg(PgStatus status) {
		return new PgPayment("pay-1", "ORD-1", status, 35000, 35000, Payment.VIRTUAL_ACCOUNT_METHOD, null,
				new PgVirtualAccount("004", "110123455678", "홍길동", DUE), "webhook-secret");
	}
}
