# Eum-ShoppingMall-backend
이음산업건설 쇼핑몰 백엔드

## 데이터베이스 마이그레이션

MySQL 스키마는 Flyway로 관리합니다. 빈 데이터베이스는 Hibernate가 엔티티 매핑을 검증하기 전에 `src/main/resources/db/migration`의 버전별 스크립트를 적용합니다. 이미 적용한 마이그레이션은 고치지 않습니다. 엔티티를 바꾸는 변경에 다음 번호의 `V<number>__description.sql` 파일을 함께 추가합니다.

재고·수량·가격·금액 컬럼에는 CHECK 제약(`ck_<테이블>_<컬럼>`, 재고·금액은 0 이상, 수량은 1 이상)을 둡니다. 새로 만드는 같은 종류의 컬럼에도 붙입니다. 앱 코드가 제약을 어기면 `409 CONCURRENT_UPDATE`가 됩니다.

`test` 프로파일은 마이그레이션 스크립트가 MySQL 전용이므로 H2 스키마 자동 생성을 쓰고 Flyway를 끕니다. CI는 빈 MySQL 8.4 데이터베이스에서 `ddl-auto: validate`로 애플리케이션을 띄웁니다. 마이그레이션이 실패하거나 엔티티와 맞지 않으면 필수 검사인 `Validate database migrations`에서 막힙니다.

운영(ECS)에서는 API 서버가 뜰 때 마이그레이션하지 않습니다(#131). 마이그레이션 전용 계정을 받는 일회성 `migrate` 작업(`SPRING_PROFILES_ACTIVE=prod,migrate`)이 Flyway를 적용하고 Hibernate 검증까지 마친 뒤 종료 코드 0으로 끝나고(`MigrationOnlyRunner`), API는 `SPRING_FLYWAY_ENABLED=false`와 DML 전용 앱 계정으로 뜹니다. CI의 `Validate database migrations`도 이 `migrate` 프로파일로 돌려 종료 코드 0을 확인합니다. 절차는 `docs/deployment-aws.md`의 "DB 계정과 마이그레이션"을 따릅니다.

`local` 프로파일도 Flyway와 Hibernate 검증을 씁니다. 이전에 Hibernate `update`로 만든 로컬 데이터베이스를 보존하려고, 이미 있는 로컬 스키마를 버전 1로 베이스라인 처리합니다. 이 베이스라인은 로컬에서만 씁니다. 운영에서는 자동 베이스라인을 켜지 않습니다. 기존 운영 데이터베이스에 Flyway를 연결하기 전에는 스키마를 확인하고 직접 베이스라인을 잡습니다. 첫 마이그레이션은 기본 카테고리나 관리자 계정을 만들지 않으며, 이 데이터는 계속 애플리케이션 부트스트랩이 맡습니다.

## 관리자 주문 취소 API

관리자는 `POST /api/v1/admin/orders/{orderId}/cancel`을 쓸 수 있습니다. JSON 본문에 200자 이하의 `reason`을 필수로 보냅니다.

- **결제 완료·상품 준비 중 주문:** 결제사를 통해 전액 환불합니다.
- **결제 대기 주문:** 만료 처리하고 잡아 둔 재고를 풉니다.
- **가상계좌 입금 대기 주문:** 만료되기 전에 결제사에서 취소하고 재고를 풉니다.
- **처리하지 않는 경우:** 배송 중이거나 배송 완료된 주문과 부분 환불은 이 API로 처리하지 않습니다.

취소에 성공하면 관리자 주문 상세 응답에 `cancelReason`, `cancelledByMemberId`, `cancelledAt`이 들어갑니다.

## 결제 정보 보관

결제 기록은 법정 보관 기간(5년) 동안 남으므로 필요한 값만 저장합니다(#144).

- **PG 응답 원문:** 저장하지 않습니다. 카드 번호 일부·승인번호 같은 결제 수단 정보가 들어 있고, 화면과 정산에 쓰는 값은 따로 저장합니다. 결제 분쟁 때는 Toss 결제 조회 API(`paymentKey`)로 원문을 다시 받습니다. 예전에 저장한 `payment.pg_raw` 값은 남아 있습니다.
- **가상계좌:** 입금이 끝나거나 입금 기한이 지나면 계좌 번호는 끝 4자리만, 입금자명은 첫 글자만 남깁니다(예: `********5678`, `홍**`). 입금이 취소돼 다시 입금 대기가 되면 Toss가 알려 준 계좌 정보로 되살립니다.

## 고정 콘텐츠 API

지원하는 콘텐츠 코드는 `company`, `terms`, `privacy-policy`입니다. 고객은 `GET /api/v1/contents/{code}`로 읽습니다(예: `GET /api/v1/contents/company`). 관리자는 `GET /api/v1/admin/contents`와 `GET /api/v1/admin/contents/{code}`로 저장된 콘텐츠를 조회하고, `PUT /api/v1/admin/contents/{code}`에 `{ "title": "...", "body": "..." }`를 보내 만들거나 수정합니다. 본문은 일반 텍스트이고 HTML 태그는 거절합니다. 서버는 법적 문구를 미리 넣어 두지 않습니다.

## 이미지 저장소

업로드 파일은 기본적으로 로컬 디스크에 저장합니다(`UPLOAD_STORAGE=local`).

S3를 쓰려면 `UPLOAD_STORAGE=s3`, `AWS_REGION`, `UPLOAD_S3_BUCKET`, `UPLOAD_CDN_BASE_URL`, `UPLOAD_S3_ACCESS_KEY`, `UPLOAD_S3_SECRET_KEY`를 설정합니다. 키는 버킷의 `products/` 경로만 쓸 수 있는 전용 IAM 사용자의 것이며, 키가 없으면 앱이 시작되지 않습니다. 서버 환경변수나 시크릿으로 넘기고 커밋하는 파일에는 넣지 않습니다.

- **저장 방식:** S3에 올린 파일은 `products/YYYY/MM/{UUID}.{extension}` 경로에, 파일 내용으로 판별한 이미지 MIME 타입과 1년 불변 캐시 헤더를 붙여 저장합니다. 클라이언트에는 CDN 주소를 돌려줍니다.
- **이미지 주소:** 상품과 배너의 `imageUrl`은 업로드 API가 돌려준 주소여야 합니다.
- **직접 업로드:** S3에서는 관리자가 직접 올릴 수도 있습니다. `POST /api/v1/uploads/presigned-url`은 Presigned PUT URL과 함께 보낼 헤더를 돌려주고, `POST /api/v1/uploads/complete`는 올린 파일을 다시 검사(이미지 시그니처, 5MB)한 뒤 CDN 주소를 돌려줍니다.
- **정리 작업:** 매일 한 번(`UPLOAD_CLEANUP_CRON`, 기본 04:30) 올린 지 24시간이 지났고 어떤 상품이나 배너도 쓰지 않는 이미지를 지웁니다.
- **인프라:** 버킷, CloudFront OAC, 업로드 IAM 사용자와 정책, 버전 관리는 `terraform/modules/photo-storage/s3.tf`에 정의되어 있습니다. `scripts/migrate-local-uploads-to-s3.sh`는 기존 로컬 파일을 복사합니다(`docs/deployment-aws.md` 참고).

## 관리자 회원 역할 API

`PATCH /api/v1/admin/users/{userId}/role`은 `{"role":"USER"}` 또는 `{"role":"ADMIN"}`을 받으며 `SUPER_ADMIN`만 호출할 수 있습니다. 자기 자신의 역할, 다른 `SUPER_ADMIN`의 역할, 탈퇴한 계정은 바꿀 수 없습니다. 정지된 계정에는 `ADMIN`을 줄 수 없습니다. 역할을 바꾸면 그 회원의 Refresh 토큰을 모두 지우고 이미 발급된 Access 토큰도 바로 무효로 만들므로, 강등된 관리자는 즉시 관리자 권한을 잃습니다. 설정에 지정한 부트스트랩 관리자는 `SUPER_ADMIN`으로 만들어지고, 이미 `ADMIN`이면 `SUPER_ADMIN`으로 승격됩니다. 같은 이메일의 기존 일반 회원은 승격되지 않습니다.

## 요청 추적과 지표

모든 응답에 `X-Request-Id`가 들어갑니다. 요청에 안전한 값(영문·숫자·점·밑줄·하이픈으로 된 1~64자)이 있으면 그대로 돌려주고, 그렇지 않으면 서버가 UUID를 만듭니다. 같은 값이 애플리케이션 로그에도 남습니다. 요청이 시작한 백그라운드 작업(메일, 알림톡)도 그 요청의 ID를 남기고, 정기 작업은 실행마다 만든 ID(`<작업 이름>-<UUID>`)를 같은 자리에 남깁니다.

`LOG_FORMAT`에 `ecs`, `logstash`, `gelf` 중 하나를 넣으면 로그 수집 도구가 읽을 한 줄 JSON으로 남깁니다. 비워 두면 텍스트 패턴으로 남습니다. 이메일, 연락처, 토큰은 로그에 남기지 않습니다. DB 제약 위반은 예외 종류와 제약 이름만 남기고, 겹친 값이 그대로 들어 있는 Hibernate의 SQL 오류 로그(`org.hibernate.orm.jdbc.error`)는 끕니다. 재고·금액 같은 CHECK 제약 위반은 코드 실수이므로 ERROR로 남깁니다. 메일 서버 설정이 없을 때도 받는 사람과 메일 본문(인증번호)은 남기지 않습니다.

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

`DELETE /api/v1/me`에는 `{"password":"..."}`가 필요합니다. 결제 대기, 결제 처리 중, 입금 대기, 결제 완료, 상품 준비 중, 배송 중, 취소 요청(환불 미확정) 상태의 주문이 있으면 탈퇴할 수 없습니다(`OrderStatus.IN_PROGRESS`). 탈퇴에 성공하면 계정은 `WITHDRAWN`이 되고, 로그인 정보와 프로필 식별 정보는 익명화되며, Refresh 토큰과 장바구니 상품, 저장한 배송지가 지워집니다. 지난 주문은 남습니다.

관리자는 `PATCH /api/v1/admin/users/{userId}/status`로 다른 계정을 정지하거나 다시 활성화할 수 있습니다. 정지하려면 사유가 필요하고, 정지하면 Refresh 토큰이 폐기됩니다.

## 회원 배송지

마이페이지와 주문서에서 쓰는 배송지 주소록입니다. 로그인한 회원 본인의 배송지만 다룹니다.

- **API:** `GET`·`POST /api/v1/me/addresses`, `PUT`·`DELETE /api/v1/me/addresses/{addressId}`. 목록은 기본 배송지가 맨 앞이고, 나머지는 최근에 고친 순서입니다.
- **필드:** `label`(배송지 이름)과 `recipient`, `recipientPhone`, `zipCode`, `address`, `addressDetail`입니다. 주문 요청의 배송지(`delivery`)와 이름이 같아, 고른 배송지를 주문서에 그대로 넣을 수 있습니다.
- **기본 배송지:** 처음 등록한 배송지와 `isDefault: true`로 저장한 배송지가 기본이 되고, 회원마다 하나만 남습니다. 기본 배송지를 지우면 최근에 고친 배송지가 기본이 됩니다.
- **제한:** 회원마다 10개까지 저장합니다(`400 ADDRESS_LIMIT_EXCEEDED`). 남의 배송지는 없는 배송지와 똑같이 `404 ADDRESS_NOT_FOUND`입니다.
- 주문은 배송지를 따로 복사해 두므로, 주소록을 고치거나 지워도 지난 주문은 그대로입니다.

## 상품 문의

상품 상세의 상품 문의(Q&A)입니다. 누구나 목록을 보고, 회원만 문의를 남깁니다.

- **고객:** `GET /api/v1/products/{productId}/questions`(최근 순, `size`는 50까지), `POST /api/v1/products/{productId}/questions`(`title`, `content`, `isPrivate`). 고객에게 보이는 상품에만 남길 수 있고, 작성은 IP별 요청 제한을 받습니다.
- **가리기:** 작성자 이름은 첫 글자와 마지막 글자만 남깁니다(`홍*동`). 비밀 문의는 작성자 본인이 아니면 제목을 "비밀 문의입니다."로 바꾸고 내용과 답변을 비웁니다. 탈퇴한 회원은 "탈퇴한 회원"으로 보입니다.
- **관리자:** `GET /api/v1/admin/product-questions`(`status`=`WAITING`·`ANSWERED`, `productId`), `GET`·`PATCH /api/v1/admin/product-questions/{questionId}`(`answer`, `adminMemo`). 답변을 남기면 답변 완료, 빈 답변이면 답변 대기로 돌아갑니다. 관리자 메모는 고객에게 보이지 않습니다. 비밀 문의와 작성자 이름이 보이므로 조회도 감사 로그에 남깁니다.

## 상품 리뷰

배송이 끝난 주문의 상품마다(주문 품목 하나에) 회원이 리뷰를 하나씩 씁니다. 누구나 목록과 요약을 봅니다.

- **고객:**
  - `GET /api/v1/products/{productId}/reviews`(`sort`=`latest`(기본)·`rating_high`·`rating_low`, `size`는 50까지), `GET /api/v1/products/{productId}/reviews/summary`(평균 별점, 별점별 수)
  - `GET /api/v1/me/reviewable-items`: 리뷰를 쓸 수 있는 내 주문 상품(배송 완료, 취소·반품 안 됨, 아직 리뷰 없음)
  - `POST /api/v1/products/{productId}/reviews`(`orderItemId`, `rating` 1~5, `content`). 남의 품목·다른 상품의 품목·배송 전 품목은 `400 REVIEW_NOT_ALLOWED`, 이미 썼으면 `409 REVIEW_ALREADY_WRITTEN`입니다.
  - 작성자 이름은 가리고(`홍*동`), 주문할 때 고른 옵션 이름을 함께 보여 줍니다. 사진 리뷰는 아직 받지 않습니다(`photoReviewCount`는 0).
- **상품 평점:** 상품 목록·상세에 `rating`(소수 첫째 자리, 리뷰가 없으면 null)과 `reviewCount`가 있습니다. 상품에 보이는 리뷰 수와 별점 합계를 저장해 두고, 리뷰를 쓰거나 숨기거나 다시 보일 때 한 쿼리로 바꿉니다.
- **관리자:** `GET /api/v1/admin/reviews`(`productId`, `visible`, `rating`), `PATCH /api/v1/admin/reviews/{reviewId}`(`visible`, `adminReply`, `adminMemo`). 숨기면 목록과 평점에서 빠집니다. 답변은 고객에게 보이고 메모는 보이지 않습니다. 목록은 작성자 이름이 보여 감사 로그에 남깁니다.

## 브랜드

브랜드 목록·상세 페이지의 브랜드 소개입니다. 상품과는 이름으로 이어집니다: 상품의 `brand` 값이 브랜드 `name`과 같으면 그 브랜드의 상품이고, 브랜드 상품 목록은 `GET /api/v1/products?brand={name}`으로 봅니다.

- **고객(로그인 없이):** `GET /api/v1/brands`(보이는 브랜드, 정렬 순서·이름 순, 고객에게 보이는 상품 수 `productCount`), `GET /api/v1/brands/{brandId}`(로고·대표 이미지·주력 분야·원산지·인증·브랜드 스토리), `GET /api/v1/brands/featured`(대표 브랜드, 없으면 `null`)
- **관리자:** `GET`·`POST /api/v1/admin/brands`, `PUT`·`DELETE /api/v1/admin/brands/{brandId}`. 이름이 같은 브랜드는 `409 BRAND_NAME_DUPLICATED`입니다. 이름을 바꾸면 이전 이름을 쓰던 상품의 브랜드도 함께 바꿉니다. 브랜드를 지워도 상품의 브랜드 값은 그대로입니다.
- **이미지:** 로고·대표 이미지는 업로드 API가 돌려준 주소만 받고, 참조 없는 이미지 정리가 지우지 않도록 알립니다(`ImageReferences`). 관리자가 입력한 글은 화면에서 텍스트로만 그립니다.
- 대표 브랜드(`featured`)가 여럿이면 정렬 순서가 앞선 하나를 씁니다.

## 개인정보 보유 기간

보유 기간이 지난 개인정보는 매일 05:30에 지웁니다(`PersonalDataRetentionCleaner`, #134). 값은 개인정보 처리방침의 보유 기간과 맞춥니다.

| 대상 | 기간(기본) | 지우는 것 | 남기는 것 |
|---|---|---|---|
| 배송·취소·만료가 끝난 주문 | 5년(`ORDER_PERSONAL_DATA_RETENTION_DAYS`, 전자상거래법 거래 기록) | 받는 사람, 연락처, 주소, 배송 요청 | 금액, 품목, 상태(매출 통계) |
| 답변한 1:1 문의 | 3년(`INQUIRY_PERSONAL_DATA_RETENTION_DAYS`, 소비자 분쟁 처리 기록) | 연락처, 이메일, 제목, 내용, 답변 | 유형, 상태, 동의 시각 |
| 완료한 견적 문의 | 3년(`QUOTE_PERSONAL_DATA_RETENTION_DAYS`) | 회사명, 담당자, 연락처, 이메일, 내용 | 상태, 동의 시각 |
| 보냈거나 최종 실패한 알림 | 90일(`NOTIFICATION_RETENTION_DAYS`) | 행 전체 | - |

- 기간은 마지막으로 바뀐 시각(`updated_at`)부터 셉니다. 진행 중인 주문, 답변 전 문의, 끝나지 않은 견적은 기간이 지나도 두고, 동의 기록은 분쟁 근거이므로 지우지 않습니다(#21). 주문·문의 기간은 법정 기간보다 짧게 설정할 수 없습니다.
- 지운 행에는 `personal_data_purged_at`이 남고, 종류별 처리 건수는 `personal.data.purged{type}` 지표로 남습니다(파기 기록). 실패하면 정기 작업 실패 알림이 갑니다(#32).
- **백업:** 지운 개인정보도 RDS 자동 백업(7일)과 Redis 스냅샷(1일)에는 그동안 남습니다. RDS를 지울 때 남기는 최종 스냅샷(`<name>-mysql-final`)은 기한 없이 남으므로, 개인정보 처리방침에 백업 보관 기간을 적고 최종 스냅샷은 필요가 없어지면 직접 지웁니다.

## API 접근 정책

어떤 API가 공개인지, 관리자 전용인지, IP별 요청 제한을 받는지, 관리자 감사 로그에 남는지는 `global/security/ApiPolicy` 표 하나에 정의합니다. 보안 설정, 요청 제한 필터, 감사 인터셉터가 모두 이 표를 읽습니다.

`ApiPolicyTests`는 컨트롤러와 표가 맞지 않으면 실패합니다.

- 관리자 표에 없거나 감사 대상이 아닌 `Admin*` 컨트롤러
- 관리자 컨트롤러가 아닌 곳에서 처리하는 `/api/v1/admin/**` 경로
- 표에 남아 있지만 실제로는 없는 경로나 요청 제한 규칙
- 요청 제한이 없는 비로그인 쓰기 API
- 요청 본문으로 비밀번호를 받는데 요청 제한이 없는 API(#162). 비밀번호 변경과 회원 탈퇴는 한 묶음으로 셉니다.

IP별 요청 제한과 로그인 잠금은 같은 Redis 카운터 구현(`SharedCounter`)을 쓰고, Redis 장애 중에는 인스턴스별로 셉니다.

## 관리자 로그인과 세션

관리자 계정은 관리자 로그인(`POST /api/v1/admin/auth/login`)으로만 들어오고, 관리자 세션은 회원 세션과 따로 씁니다(#163).

- **회원 로그인:** `POST /api/v1/auth/login`에 관리자 계정을 넣으면 비밀번호가 맞아도 `401 LOGIN_FAILED`로 답하고 실패 횟수를 셉니다. 관리자 로그인에 회원 계정을 넣어도 같습니다. 이 시도는 감사 로그(`kind=LOGIN`)에 "관리자 계정 회원 로그인 시도(거절)"로 남습니다. 관리자가 쇼핑하려면 회원 계정을 따로 만듭니다.
- **세션 분리:** 관리자 Refresh 토큰은 `admin_refresh_token` 쿠키(Path `/api/v1/admin/auth`)로 주고, 재발급·로그아웃은 `POST /api/v1/admin/auth/refresh`·`POST /api/v1/admin/auth/logout`을 씁니다. 회원 재발급(`/api/v1/auth/refresh`)은 관리자 토큰을, 관리자 재발급은 회원 토큰을 받지 않습니다.
- **관리자 화면 주소:** 관리자 재발급·로그아웃은 `CORS_ADMIN_ORIGINS`(Terraform `cors_admin_origins`)에서 온 요청만 받습니다. 그래서 고객 사이트에 스크립트 삽입 같은 문제가 생겨도 같은 브라우저의 관리자 세션으로 관리자 토큰을 받을 수 없습니다. 이 주소는 `CORS_ALLOWED_ORIGINS`에도 넣습니다. 비어 있으면 관리자는 Access 토큰이 끝날 때마다(15분) 다시 로그인해야 합니다.

관리자 로그인은 비밀번호와 OTP(TOTP) 두 단계입니다(#164). 「개인정보의 안전성 확보조치 기준」 제6조(접근통제)의 안전한 인증수단과 접속 자동 차단입니다.

1. `POST /api/v1/admin/auth/login`이 비밀번호를 확인하면 쿠키 없이 `mfaToken`(5분, 1회용)을 줍니다. `mfaSetupRequired`가 `true`면 아직 OTP를 등록하지 않은 관리자입니다.
2. 등록할 때만: `POST /api/v1/admin/auth/mfa/setup`(`{"mfaToken":"..."}`)이 OTP 앱에 넣을 `secret`과 QR 코드용 `otpauthUri`를 줍니다. Google OTP·Microsoft Authenticator 같은 TOTP 앱(30초, 6자리)을 씁니다.
3. `POST /api/v1/admin/auth/mfa/verify`(`{"mfaToken":"...","code":"123456"}`)가 코드를 확인하고 Access 토큰과 관리자 쿠키를 줍니다. 처음 맞는 코드를 넣으면 등록이 끝납니다.

- **틀린 코드:** 한 번 받은 코드는 다시 받지 않습니다. `mfaToken` 하나로 5번까지 틀릴 수 있습니다. 한 관리자가 15분 안에 10번 틀리면 그동안 `429 ADMIN_MFA_LOCKED`로 막고 담당자에게 알립니다(`manual.check.required{reason=admin_mfa_locked}`). 비밀번호가 샌 것이므로 비밀번호를 바꿉니다.
- **기기 분실·교체:** `SUPER_ADMIN`이 `DELETE /api/v1/admin/users/{userId}/mfa`로 OTP를 지웁니다. 그 관리자의 로그인이 끊기고, 다음 로그인에서 다시 등록합니다. 최고 관리자가 한 명뿐인데 기기를 잃으면 DB에서 그 관리자의 `admin_mfa` 행을 지웁니다.
- **비밀 키 보관:** OTP 비밀 키는 `JWT_SECRET`에서 뽑은 키로 암호화해(AES-256-GCM) 둡니다. `JWT_SECRET`을 바꾸면 비밀 키를 풀 수 없으므로 관리자는 다음 로그인에서 다시 등록합니다.
- **설정:** `ADMIN_MFA_REQUIRED`(기본 `true`)를 끄면 OTP를 등록하지 않은 관리자는 비밀번호만으로 들어옵니다. 등록한 관리자에게는 계속 OTP를 요구합니다. 운영에서는 끄지 않습니다.
- **접속 자동 차단:** 관리자 세션은 `ADMIN_SESSION_IDLE_TIMEOUT`(기본 30분) 동안 재발급하지 않으면 끝나고, 계속 써도 로그인한 지 `ADMIN_SESSION_MAX_LIFETIME`(기본 8시간)이 지나면 끝납니다. 마지막에 받은 Access 토큰은 그 뒤 최대 15분 더 쓸 수 있습니다. 관리자 화면은 재발급을 타이머로 미리 부르지 말고, 요청이 401로 끝났을 때처럼 사용자가 화면을 쓸 때만 부릅니다. 그래야 자리를 비운 동안 세션이 끝납니다. 회원 세션은 그대로 14일입니다.

## 로그인 시도 제한

IP별 요청 제한(`RATE_LIMIT_PER_MINUTE`)과 별도로 로그인 이메일마다 실패 횟수를 셉니다. 회원·관리자 로그인에 똑같이 적용합니다.

- **로봇 확인을 켜면(`CAPTCHA_ENABLED=true`):** 첫 실패부터 10분 안에 `LOGIN_CAPTCHA_AFTER_FAILURES`(기본 5)번 실패한 이메일은, 다음 로그인부터 로봇 확인 토큰(`captchaToken`, 위젯 action `login`)을 함께 보내야 합니다.
  - 토큰이 없으면 `400 CAPTCHA_REQUIRED`, 확인에 실패하면 `400 CAPTCHA_INVALID`입니다. 이때는 비밀번호를 비교하지 않습니다.
  - 막지는 않습니다. 그래서 남이 일부러 틀려도 주인은 로봇 확인을 하고 로그인할 수 있습니다(#141).
- **로봇 확인을 끄면:** `LOGIN_LOCK_MAX_FAILURES`(기본 10)번 실패하면, 그 10분이 지날 때까지 비밀번호가 맞아도 `429 LOGIN_LOCKED`를 돌려줍니다. 이메일만 알면 남이 막아 둘 수 있으므로, 로봇 확인 키를 받는 대로 켭니다.
- 없는 이메일도 똑같이 세므로 응답으로 계정이 있는지 알 수 없습니다. 로그인에 성공하거나 비밀번호를 재설정하면 횟수가 지워집니다. 횟수는 Redis로 공유하고, Redis 장애 중에는 인스턴스별로 셉니다.

로그인·가입·인증번호·비밀번호 재설정의 이메일은 영문·숫자·기호(ASCII)로만 받고 소문자로 맞춰 저장합니다. DB도 이메일을 글자 그대로 비교합니다(`utf8mb4_0900_bin`). 그래서 `ádmin@…`처럼 악센트만 바꾼 이메일로 다른 계정에 로그인하거나 횟수 제한을 피할 수 없습니다(#137). 테이블 기본 정렬 규칙(`utf8mb4_0900_ai_ci`)은 대소문자·악센트를 구분하지 않으므로, 값으로 행을 찾는 새 컬럼에는 `COLLATE utf8mb4_0900_bin`을 지정합니다.

## 관리자 감사 로그

관리자의 `POST`, `PUT`, `PATCH`, `DELETE` 요청(이미지 업로드 포함)은 성공했거나 권한 부족(403)으로 거절됐을 때 기록합니다. 검증 오류 같은 다른 실패는 기록하지 않습니다.

개인정보가 보이는 관리자 조회도 같은 방식으로 기록합니다(#138). 개인정보의 안전성 확보조치 기준 제8조의 접속기록입니다.

- **대상:** 회원 목록·상세(`/admin/users`), 주문 목록·상세(`/admin/orders`), 문의 목록·상세(`/admin/inquiries`), 견적 목록(`/admin/quotes`), 감사 로그 조회(`/admin/audit-logs`). 목록은 대상 ID 없이, 상세는 대상 ID와 함께 남깁니다. 상품·대시보드처럼 개인정보가 없는 조회는 남기지 않습니다(`ApiPolicy.AUDITED_READS`).
- **기록 내용:** 요청한 회원, IP, 시각, 대상, 검색 조건·쪽 번호(쿼리 문자열)를 남깁니다. 응답 본문(개인정보)은 남기지 않습니다.
- **대량 조회 알림:** 관리자 한 명이 10분 안에 개인정보 조회 API를 `ADMIN_BULK_READ_ALERT_THRESHOLD`(기본 100)번 부르면 담당자에게 알립니다(`manual.check.required{reason=admin_bulk_personal_data_read}`, #32).
- **정기 점검:** `SUPER_ADMIN`이 매월 `GET /api/v1/admin/audit-logs?kind=READ`로 지난달 조회 기록을 보고, 업무와 관계없는 회원 조회나 근무 시간 밖의 대량 조회가 없는지 확인합니다.

- **기록 내용:** 요청한 회원, 서버가 확인한 클라이언트 IP, HTTP 메서드, 대상 종류와 식별자, 경로 요약, 응답 상태, JSON 요청 본문이 남습니다. 본문에서 이름에 아래 단어가 들어간 필드는 가리고, 2,000자로 자릅니다. 감사 로그는 원래 데이터를 지운 뒤에도 2년 동안 남기 때문입니다.
  - 비밀 값: `password`, `secret`, `token`, `card`
  - 연락처: `email`, `phone`, `mobile`, `address`, `zipcode`, `recipient`
  - 고객 정보가 섞일 수 있는 글: `reason`(정지·취소·재고 조정 사유), `answer`(문의·FAQ 답변), `memo`, `note`, `comment`
- **저장 실패:** 기록을 저장하지 못하면 앱 로그에는 요청한 회원·IP·경로·대상만 ERROR로 남기고 본문은 남기지 않습니다.
- **대상:** 매칭된 경로에서 가장 안쪽의 `name/{id}`입니다. `/api/v1/admin/products/{productId}/skus/{skuId}/stock-adjustments`는 `skus`와 SKU ID를, 이미지 업로드는 `uploads`를 기록합니다.
- **조회:** `GET /api/v1/admin/audit-logs`는 요청한 회원, 대상 종류, 시간, 종류(`kind`: 개인정보 조회 `READ`·변경 `CHANGE`·관리자 로그인 `LOGIN`)로 거를 수 있고 `SUPER_ADMIN`만 쓸 수 있습니다. `fromDate`·`toDate`는 `2026-10-01`이나 `2026-10-01T09:00:00`을 받고, 날짜만 넣은 `toDate`는 그날 전체를 포함합니다.

감사 로그 API에는 수정과 삭제가 없습니다. MySQL에서는 DB 트리거(V25)가 감사 로그 수정과 364일보다 최근 기록의 삭제도 막습니다. 앱 계정이 뚫려도 최근 기록을 고치거나 지울 수 없습니다(#131). 로그는 2년(`AUDIT_LOG_RETENTION_DAYS`, 기본 730일) 보관한 뒤 매일 05:15에 1,000건씩 나눠 지웁니다. 개인정보 처리시스템 접속기록 보관 기준에 맞춰 365일보다 짧게는 설정할 수 없습니다. 조회 기록이 더해져 변경 기록보다 훨씬 많아지지만, `created_at` 인덱스(V18)로 나눠 지우므로 정리 시간은 늘어난 건수에 비례합니다. 관리자 로그인은 비밀번호 확인, OTP 확인 실패, OTP 등록·로그인 성공을 나눠 남깁니다(`kind=LOGIN`, #164).

## 해시 저장 원칙

DB·Redis에는 원래 값 대신 해시를 저장합니다(#136).

- **서버가 비교하거나 다시 찾는 값:** 서버 비밀 키를 섞은 HMAC-SHA256으로 해시합니다. 키가 DB·Redis에 없으므로 DB나 Redis가 새도 원래 값을 넣어 보며 맞힐 수 없습니다. 같은 값이면 늘 같은 해시가 나와야 다시 찾을 수 있으므로 값마다 다른 솔트는 넣지 않습니다.
  - 이메일 인증번호, 가입 확인 토큰, Refresh 토큰, 가상계좌 웹훅 비밀값
  - Redis 키에 넣는 이메일(로그인 잠금·인증번호 실패 횟수)과 클라이언트 IP(요청 제한)
- **비밀번호:** 사용자가 정해 다른 사이트에도 쓸 수 있는 값이므로, 값마다 솔트를 넣는 느린 해시(BCrypt, 강도 10)로 저장합니다.
- **키:** `JWT_SECRET`에서 용도별로 따로 뽑습니다(`SecretHashes`). 한 용도의 해시를 다른 용도에 그대로 쓸 수 없습니다.
- **`JWT_SECRET`을 바꾸면 해시도 모두 바뀝니다.**
  - 로그인 잠금·인증번호 실패·요청 제한 횟수는 처음부터 다시 셉니다.
  - 보낸 인증번호와 가입 확인 토큰은 다시 받아야 하고, 모든 회원이 다시 로그인해야 합니다.
  - 입금을 기다리는 가상계좌는 웹훅 비밀값을 확인하지 못해 입금이 자동으로 반영되지 않습니다. 입금 대기 주문이 없을 때 바꾸거나, 남은 주문은 직접 확인합니다.
- **새 방식으로 바꿀 때(이 변경을 배포할 때):** 로그인 잠금·인증번호 실패·요청 제한 횟수는 처음부터 다시 세고, 30분짜리 가입 확인 토큰은 다시 받아야 합니다. 배포 전에 받은 Refresh 토큰과 입금 대기 가상계좌는 이전 방식(키 없는 SHA-256)도 함께 확인하므로 다시 로그인하거나 다시 결제할 필요가 없습니다. 이 확인은 Refresh 토큰 유효 기간(14일)과 가상계좌 입금 기한이 지나면 지워도 됩니다.

## 이메일 인증 제한

이메일 인증번호(회원가입, 비밀번호 재설정)는 영문 대소문자와 숫자로 된 6자리이고 대소문자를 구분합니다. 헷갈리기 쉬운 `0`·`O`·`o`, `1`·`I`·`l`은 쓰지 않습니다. DB에는 서버 비밀 키를 섞은 해시(HMAC-SHA256)만 저장하므로 DB 값만으로는 인증번호를 알아낼 수 없습니다. 키는 `JWT_SECRET`에서 따로 뽑아 쓰므로, `JWT_SECRET`을 바꾸면 그 전에 보낸 인증번호는 다시 받아야 합니다.

- **보내기:** 이메일·용도별로 1분에 한 번 보낼 수 있습니다. 24시간 동안 보내는 수는 로봇 확인에 따라 다릅니다(#166).
  - **로봇 확인을 켜면:** 3통까지는 그냥 보내고, 그 뒤로는 로봇 확인 토큰(`captchaToken`, 위젯 action `send-code`)을 함께 보내야 20통까지 보냅니다. 토큰이 없으면 `400 CAPTCHA_REQUIRED`, 확인에 실패하면 `400 CAPTCHA_INVALID`입니다. 남이 한도를 채워도 주인은 로봇 확인을 하고 인증번호를 받을 수 있습니다.
  - **로봇 확인을 끄면:** 5통까지만 보냅니다(`429 VERIFICATION_DAILY_LIMIT_EXCEEDED`). 메일 폭탄은 막지만, 이메일만 알면 남이 5통을 먼저 요청해 그 회원이 하루 동안 비밀번호 재설정 인증번호를 받지 못하게 할 수 있습니다. 로봇 확인 키를 받는 대로 켭니다.
- **가입 여부 숨기기(#140):** 응답으로는 이메일의 가입 여부를 알 수 없고, 메일함의 주인만 알 수 있습니다.
  - 회원가입 인증번호 요청은 가입된 이메일에도 같은 응답을 줍니다. 대신 인증번호가 아니라 "이미 가입된 이메일입니다" 안내 메일을 보냅니다. 발송 간격과 하루 한도도 똑같이 적용합니다.
  - 회원가입은 인증 토큰을 확인한 뒤에만 `409 EMAIL_ALREADY_EXISTS`를 줍니다. 가입 이메일 인증을 끈 동안(`SIGNUP_EMAIL_VERIFICATION_REQUIRED=false`)은 토큰 없는 가입 요청의 409로 가입 여부를 알 수 있습니다.
- **틀렸을 때:**
  - 인증번호 하나는 5번까지 틀릴 수 있고, 그 뒤에는 새로 받아야 합니다(`VERIFICATION_ATTEMPTS_EXCEEDED`).
  - **로봇 확인을 켜면:** 첫 실패부터 1시간 안에 3번 틀리면, 다음 확인부터 로봇 확인 토큰(`captchaToken`)을 함께 보내야 합니다. 토큰이 없으면 `400 CAPTCHA_REQUIRED`, 확인에 실패하면 `400 CAPTCHA_INVALID`이고, 이때는 틀린 횟수가 늘지 않습니다. 막지는 않으므로 남이 일부러 틀려도 주인은 새 인증번호를 받아 인증할 수 있습니다(#141).
  - **로봇 확인을 끄면:** 5번 틀리면 그때부터 1시간 동안 그 이메일로 인증번호를 받거나 확인할 수 없습니다(`429 VERIFICATION_LOCKED`). 24시간 동안 틀린 횟수가 모두 합쳐 10번이면 하루 동안 받지 않습니다.
  - 인증번호가 없거나 만료된 경우도 틀린 것으로 셉니다.
  - 횟수는 Redis로 공유하고, Redis 장애 중에는 인스턴스별로 셉니다. 회원가입과 비밀번호 재설정은 따로 셉니다.
- **비밀번호 재설정:** 가입 여부가 드러나지 않도록, 한도를 넘거나 막힌 요청도 응답은 똑같이 성공이고 메일만 보내지 않습니다. 로봇 확인 요구(`CAPTCHA_REQUIRED`)는 가입되지 않은 이메일에도 똑같이 줍니다. 가입되지 않은 이메일에도 보내지 않는 인증번호를 만들어 두므로, 재설정 확인의 오류(`VERIFICATION_ATTEMPTS_EXCEEDED` 등)도 가입 여부와 상관없이 같습니다.
- **로봇 확인(Cloudflare Turnstile):** 기본은 꺼져 있고, 꺼져 있으면 틀린 횟수에 따른 차단만 동작합니다. 로그인(action `login`), 인증번호 받기(action `send-code`), 인증번호 확인(action `verify-code`)에 씁니다.
  - Cloudflare 대시보드에서 Turnstile 위젯을 만들고 허용 도메인에 프론트 도메인을 넣습니다. 사이트 키는 프론트에, 비밀 키는 서버의 `CAPTCHA_SECRET_KEY`에 넣고 `CAPTCHA_ENABLED=true`로 켭니다. 켰는데 비밀 키가 없으면 서버가 뜨지 않습니다.
  - 서버는 Turnstile 응답의 `success`와 함께, 위젯을 푼 사이트(`hostname`)가 `CAPTCHA_ALLOWED_HOSTNAMES`(쉼표로 구분, 켜면 필수)에 있는지와 위젯의 `action`이 그 API에 맞는지도 확인합니다. 다르면 `400 CAPTCHA_INVALID`입니다. 프론트는 인증번호 받기 위젯에 `action: "send-code"`, 인증번호 확인 화면의 위젯에 `action: "verify-code"`를 넣어야 합니다.
  - 토큰은 한 번만 쓸 수 있으므로 확인 요청마다 위젯에서 새로 받아야 합니다. Turnstile이 응답하지 않으면 `503 CAPTCHA_UNAVAILABLE`입니다.
  - 실제 키가 없어도 Cloudflare가 제공하는 테스트용 사이트 키·비밀 키로 프론트 연동을 먼저 확인할 수 있습니다.

## 배송비 정책

주문에 할인은 없고, 상품 합계에 더해지는 금액은 배송비뿐입니다. 관리자는 `GET`/`PUT /api/v1/admin/shipping-policy`(`{"shippingFee":3000,"freeShippingThreshold":50000}`)로 정합니다.

- 상품 합계가 `freeShippingThreshold` 이상이면 무료배송입니다.
- `freeShippingThreshold`를 빼면 항상 배송비를 받습니다.
- `shippingFee`가 0이면 무료배송입니다.
- 관리자가 정책을 저장하기 전에는 100,000원 미만일 때 배송비가 5,000원입니다.

장바구니 예상 금액과 새 주문은 현재 정책을 씁니다. 주문마다 만들 때의 배송비를 저장하므로 정책을 바꿔도 기존 주문은 달라지지 않습니다. `GET /api/v1/shipping-policy`는 쇼핑몰 안내용으로 로그인 없이 정책을 돌려줍니다. 장바구니 응답에는 이제 `discountAmount`가 없습니다. 주문 응답에는 변경 전에 만든 주문을 위해 남아 있고, 새 주문은 0으로 기록합니다.

## 상품 조회 캐시

공개 상품 조회(홈, 카테고리, 목록·검색, 필터 개수, 자동 완성)는 Redis에 `PRODUCT_READ_CACHE_TTL_SECONDS`(기본 60)초 동안 캐시합니다. 관리자가 상품 정보를 바꿀 때만 캐시 전체를 지웁니다. 주문으로 바뀐 재고와 판매량은 TTL 안에 반영되고, 상품 상세, 장바구니, 주문 확인은 항상 데이터베이스를 읽습니다. `product.read.cache{result=hit|miss|unavailable}`로 조회 결과를 집계합니다.

## 약관 동의

- **회원가입:** `POST /api/v1/auth/signup`에 `termsAgreed`(이용약관), `privacyAgreed`(개인정보 수집·이용)를 `true`로 보내야 합니다. 빠지거나 `false`면 400입니다. `marketingAgreed`(마케팅 수신)는 선택이고, 보내지 않으면 동의하지 않은 것으로 기록합니다.
- **비회원 문의·견적:** `POST /api/v1/support/inquiries`, `POST /api/v1/quotes`에 `privacyAgreed: true`가 없으면 `400 PRIVACY_CONSENT_REQUIRED`입니다. 회원은 가입할 때 동의했으므로 보내지 않아도 됩니다.
- **기록:** 회원 동의는 `member_consent`에 항목별로 동의 여부와 시각을 남깁니다. 비회원 동의는 문의·견적 행의 `privacy_agreed_at`에 남깁니다. 둘 다 그때의 약관·개인정보 처리방침 버전(고정 콘텐츠 `terms`, `privacy-policy`의 수정 시각)을 함께 남기며, 문서가 아직 등록되지 않았으면 버전은 비워 둡니다. 동의 기록은 탈퇴해도 지우지 않습니다.

