# Eum-ShoppingMall-backend
이음산업건설 쇼핑몰 백엔드

## 데이터베이스 마이그레이션

MySQL 스키마는 Flyway로 관리합니다. 빈 데이터베이스는 Hibernate가 엔티티 매핑을 검증하기 전에 `src/main/resources/db/migration`의 버전별 스크립트를 적용합니다. 이미 적용한 마이그레이션은 고치지 않습니다. 엔티티를 바꾸는 변경에 다음 번호의 `V<number>__description.sql` 파일을 함께 추가합니다.

`test` 프로파일은 마이그레이션 스크립트가 MySQL 전용이므로 H2 스키마 자동 생성을 쓰고 Flyway를 끕니다. CI는 빈 MySQL 8.4 데이터베이스에서 `ddl-auto: validate`로 애플리케이션을 띄웁니다. 마이그레이션이 실패하거나 엔티티와 맞지 않으면 필수 검사인 `Validate database migrations`에서 막힙니다.

`local` 프로파일도 Flyway와 Hibernate 검증을 씁니다. 이전에 Hibernate `update`로 만든 로컬 데이터베이스를 보존하려고, 이미 있는 로컬 스키마를 버전 1로 베이스라인 처리합니다. 이 베이스라인은 로컬에서만 씁니다. 운영에서는 자동 베이스라인을 켜지 않습니다. 기존 운영 데이터베이스에 Flyway를 연결하기 전에는 스키마를 확인하고 직접 베이스라인을 잡습니다. 첫 마이그레이션은 기본 카테고리나 관리자 계정을 만들지 않으며, 이 데이터는 계속 애플리케이션 부트스트랩이 맡습니다.

## 관리자 주문 취소 API

관리자는 `POST /api/v1/admin/orders/{orderId}/cancel`을 쓸 수 있습니다. JSON 본문에 200자 이하의 `reason`을 필수로 보냅니다.

- **결제 완료·상품 준비 중 주문:** 결제사를 통해 전액 환불합니다.
- **결제 대기 주문:** 만료 처리하고 잡아 둔 재고를 풉니다.
- **가상계좌 입금 대기 주문:** 만료되기 전에 결제사에서 취소하고 재고를 풉니다.
- **처리하지 않는 경우:** 배송 중이거나 배송 완료된 주문과 부분 환불은 이 API로 처리하지 않습니다.

취소에 성공하면 관리자 주문 상세 응답에 `cancelReason`, `cancelledByMemberId`, `cancelledAt`이 들어갑니다.

## 고정 콘텐츠 API

지원하는 콘텐츠 코드는 `company`, `terms`, `privacy-policy`입니다. 고객은 `GET /api/v1/contents/{code}`로 읽습니다(예: `GET /api/v1/contents/company`). 관리자는 `GET /api/v1/admin/contents`와 `GET /api/v1/admin/contents/{code}`로 저장된 콘텐츠를 조회하고, `PUT /api/v1/admin/contents/{code}`에 `{ "title": "...", "body": "..." }`를 보내 만들거나 수정합니다. 본문은 일반 텍스트이고 HTML 태그는 거절합니다. 서버는 법적 문구를 미리 넣어 두지 않습니다.

## 이미지 저장소

업로드 파일은 기본적으로 로컬 디스크에 저장합니다(`UPLOAD_STORAGE=local`).

S3를 쓰려면 `UPLOAD_STORAGE=s3`, `AWS_REGION`, `UPLOAD_S3_BUCKET`, `UPLOAD_CDN_BASE_URL`, `UPLOAD_S3_ACCESS_KEY`, `UPLOAD_S3_SECRET_KEY`를 설정합니다. 키는 버킷의 `products/` 경로만 쓸 수 있는 전용 IAM 사용자의 것이며, 키가 없으면 앱이 시작되지 않습니다. 서버 환경변수나 시크릿으로 넘기고 커밋하는 파일에는 넣지 않습니다.

- **저장 방식:** S3에 올린 파일은 `products/YYYY/MM/{UUID}.{extension}` 경로에, 파일 내용으로 판별한 이미지 MIME 타입과 1년 불변 캐시 헤더를 붙여 저장합니다. 클라이언트에는 CDN 주소를 돌려줍니다.
- **이미지 주소:** 상품과 배너의 `imageUrl`은 업로드 API가 돌려준 주소여야 합니다.
- **직접 업로드:** S3에서는 관리자가 직접 올릴 수도 있습니다. `POST /api/v1/uploads/presigned-url`은 Presigned PUT URL과 함께 보낼 헤더를 돌려주고, `POST /api/v1/uploads/complete`는 올린 파일을 다시 검사(이미지 시그니처, 5MB)한 뒤 CDN 주소를 돌려줍니다.
- **정리 작업:** 매일 한 번(`UPLOAD_CLEANUP_CRON`, 기본 04:30) 올린 지 24시간이 지났고 어떤 상품이나 배너도 쓰지 않는 이미지를 지웁니다.
- **인프라:** 버킷, CloudFront OAC, 업로드 IAM 사용자와 정책, 버전 관리는 `terraform/uploads_s3.tf`에 정의되어 있습니다. `scripts/migrate-local-uploads-to-s3.sh`는 기존 로컬 파일을 복사합니다(`docs/deployment-aws.md` 참고).

## 관리자 회원 역할 API

`PATCH /api/v1/admin/users/{userId}/role`은 `{"role":"USER"}` 또는 `{"role":"ADMIN"}`을 받으며 `SUPER_ADMIN`만 호출할 수 있습니다. 자기 자신의 역할, 다른 `SUPER_ADMIN`의 역할, 탈퇴한 계정은 바꿀 수 없습니다. 정지된 계정에는 `ADMIN`을 줄 수 없습니다. 역할을 바꾸면 그 회원의 Refresh 토큰을 모두 지우고 이미 발급된 Access 토큰도 바로 무효로 만들므로, 강등된 관리자는 즉시 관리자 권한을 잃습니다. 설정에 지정한 부트스트랩 관리자는 `SUPER_ADMIN`으로 만들어지고, 이미 `ADMIN`이면 `SUPER_ADMIN`으로 승격됩니다. 같은 이메일의 기존 일반 회원은 승격되지 않습니다.

## 요청 추적과 지표

모든 응답에 `X-Request-Id`가 들어갑니다. 요청에 안전한 값(영문·숫자·점·밑줄·하이픈으로 된 1~64자)이 있으면 그대로 돌려주고, 그렇지 않으면 서버가 UUID를 만듭니다. 같은 값이 애플리케이션 로그에도 남습니다. 요청이 시작한 백그라운드 작업(메일, 알림톡)도 그 요청의 ID를 남기고, 정기 작업은 실행마다 만든 ID(`<작업 이름>-<UUID>`)를 같은 자리에 남깁니다.

`LOG_FORMAT`에 `ecs`, `logstash`, `gelf` 중 하나를 넣으면 로그 수집 도구가 읽을 한 줄 JSON으로 남깁니다. 비워 두면 텍스트 패턴으로 남습니다. 이메일, 연락처, 토큰은 로그에 남기지 않습니다.

Actuator는 `health`와 `metrics`를 켜 둡니다. 헬스 프로브만 공개이고 나머지 Actuator 경로는 관리자 토큰이 있어야 합니다.

사람이 확인해야 하는 상황(PG 금액 불일치, 자동 환불 실패, 주문 상태와 맞지 않는 입금, 멈춘 취소 복구, 모든 시도가 실패한 알림)은 `[수동 확인 필요: reason]`으로 로그에 남고 `manual.check.required{reason}`로 집계합니다.

정기 작업은 `ScheduledJobRunner`로 실행합니다. 데이터베이스 임대(lease)를 잡아 인스턴스 하나만 각 작업을 실행하게 하고, `scheduled.job.failures{job}`(작업 예외와 항목별 실패)와 `scheduled.job.lock.lost{job}`(작업이 끝나기 전에 임대가 만료됨)를 집계합니다. PG 호출 결과는 `pg.requests{outcome=success|rejected|unavailable}`로 집계합니다.

### 담당자 알림

`ALERT_EMAILS`(쉼표로 구분)의 주소로 아래 상황을 메일로 알립니다. `MAIL_HOST`로 메일 서버도 설정해야 실제로 보내고, `ALERT_EMAILS`가 비어 있으면 로그와 지표로만 남습니다. 지표 수집 도구 없이 앱이 직접 판단해 보내는 방식입니다.

| 상황 | 기준 | 다시 알리는 간격 |
|---|---|---|
| 수동 확인 필요 | 발생할 때마다 | 같은 내용은 6시간 |
| 정기 작업 실패, 잠금 상실 | 발생할 때마다 | 작업별 1시간 |
| 5xx 응답 | 5분 동안 20건 이상 요청 중 5% 이상 | 1시간 |
| DB 커넥션 대기 | 5분 동안 획득 시간 초과가 있거나 평균 대기 1초 이상 | 1시간 |
| PG 호출 실패 | 5분 동안 5건 이상 호출 중 20% 이상 | 1시간 |

- 1분마다 쌓인 내용을 메일 한 통으로 묶어 보냅니다. 메일 서버 장애로 아무도 받지 못하면 다음 차례에 다시 보내고, `operator.alert.delivery.failures`로 집계합니다.
- 수치와 쌓인 알림은 서버별 메모리에 있으므로 서버가 여러 대면 서버마다 따로 보냅니다. 서버가 멈추면 알림도 멈추므로, 서버 생존은 로드밸런서 헬스 체크 등 바깥에서 확인해야 합니다.

## 회원 탈퇴와 정지

`DELETE /api/v1/me`에는 `{"password":"..."}`가 필요합니다. 결제 대기, 결제 처리 중, 입금 대기, 결제 완료, 상품 준비 중, 배송 중, 취소 요청(환불 미확정) 상태의 주문이 있으면 탈퇴할 수 없습니다(`OrderStatus.IN_PROGRESS`). 탈퇴에 성공하면 계정은 `WITHDRAWN`이 되고, 로그인 정보와 프로필 식별 정보는 익명화되며, Refresh 토큰과 장바구니 상품이 지워집니다. 지난 주문은 남습니다.

관리자는 `PATCH /api/v1/admin/users/{userId}/status`로 다른 계정을 정지하거나 다시 활성화할 수 있습니다. 정지하려면 사유가 필요하고, 정지하면 Refresh 토큰이 폐기됩니다.

## API 접근 정책

어떤 API가 공개인지, 관리자 전용인지, IP별 요청 제한을 받는지, 관리자 감사 로그에 남는지는 `global/security/ApiPolicy` 표 하나에 정의합니다. 보안 설정, 요청 제한 필터, 감사 인터셉터가 모두 이 표를 읽습니다.

`ApiPolicyTests`는 컨트롤러와 표가 맞지 않으면 실패합니다.

- 관리자 표에 없거나 감사 대상이 아닌 `Admin*` 컨트롤러
- 관리자 컨트롤러가 아닌 곳에서 처리하는 `/api/v1/admin/**` 경로
- 표에 남아 있지만 실제로는 없는 경로나 요청 제한 규칙
- 요청 제한이 없는 비로그인 쓰기 API

IP별 요청 제한과 로그인 잠금은 같은 Redis 카운터 구현(`SharedCounter`)을 쓰고, Redis 장애 중에는 인스턴스별로 셉니다.

## 로그인 시도 제한

IP별 요청 제한(`RATE_LIMIT_PER_MINUTE`)과 별도로 로그인 이메일마다 횟수를 제한합니다. 첫 실패부터 10분 안에 `LOGIN_LOCK_MAX_FAILURES`(기본 10)번 실패하면, 그 10분이 지날 때까지 해당 이메일의 회원·관리자 로그인은 비밀번호가 맞아도 `429 LOGIN_LOCKED`를 돌려줍니다. 없는 이메일도 똑같이 세므로 응답으로 계정이 있는지 알 수 없습니다. 로그인에 성공하거나 비밀번호를 재설정하면 횟수가 지워집니다. 횟수는 Redis로 공유하고, Redis 장애 중에는 인스턴스별로 셉니다.

## 관리자 감사 로그

관리자의 `POST`, `PUT`, `PATCH`, `DELETE` 요청(이미지 업로드 포함)은 성공했거나 권한 부족(403)으로 거절됐을 때 기록합니다. 검증 오류 같은 다른 실패는 기록하지 않습니다.

- **기록 내용:** 요청한 회원, 서버가 확인한 클라이언트 IP, HTTP 메서드, 대상 종류와 식별자, 경로 요약, 응답 상태, JSON 요청 본문이 남습니다. 본문의 민감한 필드(`password`, `secret`, `token`, `card`)는 가리고 2,000자로 자릅니다.
- **대상:** 매칭된 경로에서 가장 안쪽의 `name/{id}`입니다. `/api/v1/admin/products/{productId}/skus/{skuId}/stock-adjustments`는 `skus`와 SKU ID를, 이미지 업로드는 `uploads`를 기록합니다.
- **조회:** `GET /api/v1/admin/audit-logs`는 요청한 회원, 대상 종류, 시간으로 거를 수 있고 `SUPER_ADMIN`만 쓸 수 있습니다. `fromDate`·`toDate`는 `2026-10-01`이나 `2026-10-01T09:00:00`을 받고, 날짜만 넣은 `toDate`는 그날 전체를 포함합니다.

감사 로그 API에는 수정과 삭제가 없습니다. 선택 사항인 관리자 2FA와 회원 개인정보 열람 감사는 별도 후속 작업으로 남아 있습니다.

## 이메일 인증 제한

이메일 인증번호(회원가입, 비밀번호 재설정)는 이메일·용도별로 24시간에 최대 5번 보낼 수 있고, 그동안 틀린 번호는 모두 합쳐 최대 10번까지 받습니다(`429 VERIFICATION_DAILY_LIMIT_EXCEEDED`). 한도를 넘은 비밀번호 재설정 요청도 응답은 똑같이 성공이지만 메일은 보내지 않습니다.

## 배송비 정책

주문에 할인은 없고, 상품 합계에 더해지는 금액은 배송비뿐입니다. 관리자는 `GET`/`PUT /api/v1/admin/shipping-policy`(`{"shippingFee":3000,"freeShippingThreshold":50000}`)로 정합니다.

- 상품 합계가 `freeShippingThreshold` 이상이면 무료배송입니다.
- `freeShippingThreshold`를 빼면 항상 배송비를 받습니다.
- `shippingFee`가 0이면 무료배송입니다.
- 관리자가 정책을 저장하기 전에는 100,000원 미만일 때 배송비가 5,000원입니다.

장바구니 예상 금액과 새 주문은 현재 정책을 씁니다. 주문마다 만들 때의 배송비를 저장하므로 정책을 바꿔도 기존 주문은 달라지지 않습니다. `GET /api/v1/shipping-policy`는 쇼핑몰 안내용으로 로그인 없이 정책을 돌려줍니다. 장바구니 응답에는 이제 `discountAmount`가 없습니다. 주문 응답에는 변경 전에 만든 주문을 위해 남아 있고, 새 주문은 0으로 기록합니다.

## 상품 조회 캐시

공개 상품 조회(홈, 카테고리, 목록·검색, 필터 개수, 자동 완성)는 Redis에 `PRODUCT_READ_CACHE_TTL_SECONDS`(기본 60)초 동안 캐시합니다. 관리자가 상품 정보를 바꿀 때만 캐시 전체를 지웁니다. 주문으로 바뀐 재고와 판매량은 TTL 안에 반영되고, 상품 상세, 장바구니, 주문 확인은 항상 데이터베이스를 읽습니다. `product.read.cache{result=hit|miss|unavailable}`로 조회 결과를 집계합니다.
