# AWS 배포 구성

## 구성

`Application Load Balancer → ECS Fargate (private subnets) → RDS MySQL / ElastiCache Redis`로 구성합니다. ALB와 앱 사이에만 애플리케이션 포트(8080)를 열고, DB·Redis는 앱 보안 그룹에서만 접근하게 합니다. HTTPS는 ALB에서 종료하고 HTTP 요청은 HTTPS로 리다이렉트합니다.

Terraform은 VPC(공용·앱·데이터 서브넷 2개 AZ), NAT Gateway 1개, ALB, ECS, ECR, RDS MySQL 8.4, TLS/AUTH Redis, 업로드 이미지용 비공개 S3 버킷과 CloudFront, 암호화된 EFS 업로드 볼륨, CloudWatch 로그 그룹, 월 예산 경보를 관리합니다. 쓰지 않아도 요금이 나오는 리소스는 `enable_*` 변수로 켠 것만 만듭니다(아래 "비용과 구성 단계"). 기본 용량은 개발 검증에 맞춘 최소치입니다. `db_multi_az=false`, ECS 1개, Redis 단일 노드는 운영 고가용성 설정이 아닙니다. 운영 전에는 예상 비용을 확인하고 DB Multi-AZ, ECS 2개 이상, Redis 복제/자동 장애 조치를 정해야 합니다. 단일 NAT Gateway는 비용을 줄이는 대신 NAT 장애 시 앱 서브넷의 외부 통신도 영향을 받습니다.

애플리케이션은 업무 시간대를 `Asia/Seoul`로 고정하고 Hibernate JDBC에도 같은 시간대를 지정합니다. API의 `LocalDateTime` 값은 시간대 오프셋을 포함하지 않으므로 한국 현지 시각으로 해석해야 합니다. DB의 `DATETIME(6)` 값도 같은 기준으로 저장됩니다.

ALB의 기본 X-Forwarded-For 처리 모드는 `append`입니다. 앱의 `TRUSTED_PROXY_CIDRS`에는 ALB가 위치한 정확한 공용 서브넷 CIDR만 전달하고, ECS 작업 포트는 ALB 보안 그룹에서만 받습니다. AWS 문서: [ALB forwarded headers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html), [Fargate task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html).

업로드 이미지는 `upload_storage` 변수로 저장소를 고릅니다. 기본값 `local`은 암호화된 EFS에 저장하고 앱이 `/files/**`로 내려줍니다. `s3`로 바꾸면 비공개 S3 버킷(퍼블릭 액세스 차단)에 저장하고 CloudFront(OAC)로만 내보냅니다. 앱은 업로드 전용 IAM 사용자(`terraform output uploads_iam_user`)의 액세스 키로 버킷에 접근합니다. 이 사용자는 버킷의 `products/` 아래만 읽고 쓰고 지울 수 있습니다. 시크릿 키가 Terraform state에 남지 않도록 액세스 키는 콘솔이나 `aws iam create-access-key`로 따로 만들어 Secrets Manager에 넣고, 그 ARN을 `application_secret_arns`의 `UPLOAD_S3_ACCESS_KEY`, `UPLOAD_S3_SECRET_KEY`로 넘깁니다. 키는 주기적으로 새로 만들어 바꾸고 이전 키는 비활성화합니다. 이미 EFS에 있는 이미지는 아래 "업로드 이미지를 S3로 옮기기"를 마친 뒤에 `s3`로 바꿉니다.

앱은 매일 04:30(`UPLOAD_CLEANUP_CRON`)에 상품·배너가 쓰지 않는 이미지 중 올린 지 24시간이 지난 파일을 지웁니다(한 번에 최대 1,000개). 버킷은 버전 관리를 켜 두었으므로 지운 파일도 30일 동안 이전 버전으로 되살릴 수 있습니다.

## 비용과 구성 단계

쓰지 않아도 요금이 나오는 리소스는 변수로 켠 것만 만듭니다. 변수의 기본값은 모두 `false`이고, 모두 끄면 고정 비용이 없습니다.

| 구성 | 켜는 변수 | 고정 비용(월) | plan이 만드는 리소스 |
|---|---|---|---|
| 기본 | 없음 | $0 | 42개 |
| 데이터 준비(스키마 작업) | `enable_data_layer` | 약 $39 | 46개 |
| 개발 검증 배포 | 위 + `enable_nat_gateway`, `enable_alb`, `enable_efs`, `enable_ecs_service` | 약 $130 | 62개 |
| 운영 예시 | 위 + `db_multi_az=true`, `desired_count=2` | 약 $173 | 62개 |

기본 구성은 VPC와 서브넷·라우팅, 보안 그룹, DB·Redis 서브넷 그룹, IAM, ECS 클러스터, ECR, 업로드 S3와 CloudFront, 로그 그룹, 예산 경보만 만듭니다. 이 중 요금이 나오는 것은 ECR, S3, CloudFront, 로그처럼 쓴 만큼 내는 항목뿐입니다. 리소스 수는 `infra/aws/tests`와 같은 mock provider로 돌린 plan 결과입니다.

고정 비용의 계산 근거는 다음과 같습니다. 서울 리전 온디맨드 가격을 AWS Price List(`https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/{서비스 코드}/current/ap-northeast-2/index.json`)에서 2026-11-02에 확인했고, 한 달을 730시간으로 계산했습니다. 프리 티어와 세금은 뺐습니다. 실제 요금은 적용 전에 다시 확인합니다.

| 항목 | 변수 | 계산 | 월 비용 |
|---|---|---|---|
| NAT Gateway, 공인 IPv4 1개 | `enable_nat_gateway` | ($0.059 + $0.005) × 730시간 | $46.72 |
| ALB, 공인 IPv4 최소 2개(AZ당 1개) | `enable_alb` | ($0.0225 + $0.005 × 2) × 730시간 | $23.73 |
| RDS `db.t4g.micro` Single-AZ, gp3 20GB | `enable_data_layer` | $0.025 × 730시간 + $0.131 × 20GB | $20.87 |
| RDS `db.t4g.micro` Multi-AZ, gp3 20GB | `db_multi_az` | $0.051 × 730시간 + $0.262 × 20GB | $42.47 |
| Redis `cache.t4g.micro` 1개 | `enable_data_layer` | $0.024 × 730시간 | $17.52 |
| 시크릿 2개(Redis, RDS 마스터) | `enable_data_layer` | $0.40 × 2개 | $0.80 |
| Fargate 태스크 1개(0.5 vCPU, 1GB) | `enable_ecs_service` | ($0.04656 × 0.5 + $0.00511) × 730시간 | $20.72 |

- **쓴 만큼 더 나오는 항목:** NAT 처리량($0.059/GB), ALB LCU($0.008/LCU-시간), EFS($0.33/GB-월), ECR($0.10/GB-월), S3, CloudFront, CloudWatch Logs. EFS는 최소 요금이 없어 비어 있으면 저장 비용이 없습니다.
- **Terraform 밖의 비용:** 직접 만드는 앱 시크릿(`JWT_SECRET`, DB 앱 계정 등)은 개당 월 $0.40입니다.

### 예산 경보

`budget_limit_usd`와 `budget_alert_emails`는 기본값이 없어 팀에서 정한 값을 넣어야 plan이 통과합니다. 예제 파일의 값(`0`, `example.com` 주소)은 일부러 거절합니다.

- AWS 계정 전체 비용과 비교합니다. 실제 비용이 `budget_alert_thresholds`(기본 50%, 80%, 100%)를 넘을 때와 예측 비용이 100%를 넘을 때 메일을 보냅니다.
- 예측 비용 알림은 비용 기록이 약 5주 쌓여야 동작합니다. 그 전에는 실제 비용 알림만 옵니다.
- 예산과 알림은 무료입니다. 예산은 쓰려는 구성의 고정 비용보다 높게 잡습니다.

참고: [AWS Budgets 요금](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/), [AWS Budgets 권장 사항](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-best-practices.html)

### 켜고 끌 때 주의할 점

- **이미 apply한 환경:** 변수 기본값이 `false`이므로 tfvars에 켜 둔 리소스를 `true`로 적지 않으면 지우는 plan이 나옵니다. 값을 적은 뒤 plan에서 지워지는 리소스가 없는지 확인합니다. 모두 켜면 달라지는 리소스가 없고, 주소가 `[0]`으로 바뀌는 것은 Terraform이 알아서 옮깁니다.
- **`enable_alb`:** 지웠다가 다시 만들면 DNS 이름이 바뀌므로 API 도메인을 다시 연결합니다.
- **`enable_data_layer`:** 끄면 Redis와 시크릿은 지워지지만 RDS는 삭제 보호 때문에 `apply`가 실패합니다. RDS를 정말 지우려면 삭제 보호를 먼저 풀어야 하고, 최종 스냅샷(`<name>-mysql-final`)이 남아 보관 비용이 나옵니다. Redis 시크릿은 삭제 후 7일 동안 이름을 다시 쓸 수 없어 그 안에 다시 켜면 실패합니다.
- **`enable_efs`:** 끄면 EFS의 파일도 지워집니다.

## 실제 AWS 생성 전에 필요한 것

- AWS 계정의 배포 전용 IAM Identity Center/SSO 권한과 AWS CLI. 액세스 키를 저장소나 채팅에 넣지 않습니다.
- `ap-northeast-2`의 검증 완료된 ACM 인증서와 그 인증서에 포함된 API 도메인 DNS 권한.
- AWS Secrets Manager의 `JWT_SECRET`(강한 랜덤 32바이트 이상), `{"username":"shopping_app","password":"..."}` JSON DB 앱 계정 secret, 실제 프론트 CORS origin, 결제 사용 시 Toss secret.
- Terraform 상태를 보관할 비공개 S3 버킷. 버전 관리, 기본 암호화, 퍼블릭 액세스 차단을 켜고 `backend.hcl.example`을 복사해 실제 버킷명으로 채웁니다. Redis AUTH 값은 Terraform state에도 저장되므로 state 접근 권한을 제한합니다.
- 앱 secret은 기본 AWS 관리형 KMS 키로 암호화하는 구성을 가정합니다. 고객 관리형 KMS 키를 쓰면 ECS 실행 역할에 해당 키의 `kms:Decrypt` 권한을 정확히 추가해야 합니다.
- 팀에서 확정한 월 예산과 알림 받을 이메일. 배포 권한에는 AWS Budgets 생성·조회·태그 권한이 있어야 합니다.
- 현재 앱은 운영에서 `ddl-auto: validate`를 사용합니다. 빈 RDS에 테이블을 만드는 마이그레이션이 먼저 필요합니다(Issue #27). 별도 DB 관리자 계정으로 스키마를 만들고 `shopping_app`에는 앱 구동에 필요한 SELECT/INSERT/UPDATE/DELETE 권한만 부여한 후 `enable_ecs_service=true`로 바꿉니다. Terraform은 이 준비가 끝날 때까지 ECS 서비스를 만들지 않습니다.

Terraform은 리소스를 생성해 과금할 수 있습니다. 아래 `apply`는 비용 검토와 계정/리전 확인 뒤 운영자가 실행합니다. 이 저장소 변경에서는 AWS 리소스를 만들지 않습니다.

## 배포 절차

1. Docker와 Terraform 1.10 이상, AWS CLI를 설치합니다. SSO 프로파일로 로그인하고 `aws sts get-caller-identity`로 계정을 확인합니다.
2. `infra/aws/terraform.tfvars.example`을 `infra/aws/terraform.tfvars`로 복사하고 인증서 ARN, 도메인 origin, secret ARN, 월 예산과 알림 이메일을 채웁니다. 실제 secret 값은 AWS Secrets Manager에 직접 넣습니다. `terraform.tfvars`는 Git에 추가하지 않습니다.
3. `infra/aws/backend.hcl.example`을 `backend.hcl`로 복사해 상태 버킷명을 채웁니다. 셸에서 `TF_VAR_redis_auth_token`을 안전하게 주입한 뒤 실행합니다.

   ```sh
   cd infra/aws
   terraform init -backend-config=backend.hcl
   terraform validate
   terraform plan
   ```

4. 첫 배포는 모든 `enable_*`를 `false`로 둔 채 실행합니다. ECR 저장소, 네트워크, 업로드 버킷, 예산 경보처럼 고정 비용이 없는 리소스만 만듭니다. 이 명령은 AWS 계정에 리소스를 생성합니다.

   ```sh
   terraform apply
   ```

5. 저장소 루트에서 이미지 빌드 후 해당 ECR 저장소에 Git commit SHA 태그로 push합니다. ECR 로그인 명령에서 출력하는 비밀번호를 파일에 저장하지 않습니다.

   ```sh
   IMAGE_TAG=$(git rev-parse --short=12 HEAD)
   ECR_REPOSITORY=$(cd infra/aws && terraform output -raw ecr_repository_url)
   AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
   AWS_REGION=ap-northeast-2
   aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
   docker build -t "$ECR_REPOSITORY:$IMAGE_TAG" .
   docker push "$ECR_REPOSITORY:$IMAGE_TAG"
   ```

6. `terraform.tfvars`의 `image_tag`를 같은 SHA로 설정하고 `enable_data_layer=true`로 바꿉니다. 다시 `terraform plan`을 검토한 뒤에만 `terraform apply`를 실행합니다. Terraform이 Secret Manager에 Redis AUTH 값을 저장하고 ElastiCache/ECS에 전달합니다.
7. RDS가 생성되면 AWS가 관리하는 `rds_master_secret_arn`으로 승인된 비공개 DB 접속 경로에서 스키마와 제한된 앱 DB 계정을 준비합니다. DB 앱 secret에는 `username`, `password` 키를 넣습니다. `shopping_app`에는 `shopping` 데이터베이스의 `SELECT, INSERT, UPDATE, DELETE` 권한만 주고, 관리자 계정은 앱에 전달하지 않습니다. SQL 마이그레이션(Issue #27) 없이 앱 서비스를 켜지 않습니다.
8. `terraform.tfvars`에서 `enable_nat_gateway`, `enable_alb`, `enable_efs`, `enable_ecs_service`를 `true`로 바꾸고 `terraform plan`을 검토한 뒤 `terraform apply`를 실행합니다.
9. ALB DNS를 API 도메인으로 연결하고 `/actuator/health/readiness`, HTTPS 리다이렉트, 로그, 업로드 유지, 신뢰하지 않는 X-Forwarded-For 위조 차단을 실제 AWS에서 확인합니다.

## 업로드 이미지를 S3로 옮기기

EFS에 쌓인 이미지를 S3로 옮기고 DB의 `/files/...` 주소를 CloudFront 주소로 바꿉니다. 옮기는 동안 관리자의 이미지 업로드를 멈춥니다.

1. `upload_storage = "local"`인 상태로 `terraform apply`를 실행해 버킷과 CloudFront를 만듭니다. `terraform output uploads_bucket`, `terraform output uploads_cdn_url`로 값을 확인합니다.
2. EFS를 마운트할 수 있고 버킷에 쓸 권한이 있는 곳(같은 VPC의 임시 EC2 등)에서 스크립트를 실행합니다. `--dry-run`으로 옮길 파일을 먼저 확인합니다. 파일은 `products/legacy/{파일 이름}`으로 복사되고, 같은 폴더에 DB 주소를 바꾸는 `rewrite-local-image-urls.sql`이 만들어집니다.

   ```sh
   UPLOAD_DIR=/mnt/uploads UPLOAD_S3_BUCKET=<uploads_bucket> UPLOAD_CDN_BASE_URL=<uploads_cdn_url>      scripts/migrate-local-uploads-to-s3.sh --dry-run
   UPLOAD_DIR=/mnt/uploads UPLOAD_S3_BUCKET=<uploads_bucket> UPLOAD_CDN_BASE_URL=<uploads_cdn_url>      scripts/migrate-local-uploads-to-s3.sh
   ```

3. CloudFront 주소로 이미지 몇 개가 열리는지 확인한 뒤, 승인된 DB 접속 경로에서 `rewrite-local-image-urls.sql`을 실행합니다. 마지막 SELECT에 남는 행은 앱이 만들지 않은 형식의 주소이므로 직접 확인해 바꿉니다.
4. 업로드 IAM 사용자의 액세스 키를 만들어 Secrets Manager에 넣고 `application_secret_arns`에 `UPLOAD_S3_ACCESS_KEY`, `UPLOAD_S3_SECRET_KEY`를 추가합니다. `terraform.tfvars`에서 `upload_storage = "s3"`로 바꾸고 `terraform plan` 검토 후 `terraform apply`로 배포합니다. 새 업로드가 CloudFront 주소로 나오고 기존 상품·배너 이미지가 보이는지 확인합니다.
5. 문제가 없으면 `enable_efs=false`로 바꿔 EFS 볼륨과 마운트 설정을 지웁니다. EFS의 파일도 함께 지워지므로 모든 파일을 옮겼는지 먼저 확인하고 `terraform plan`을 검토합니다.

관리자 화면은 지금처럼 `POST /api/v1/uploads`(multipart)로 올려도 되고, S3에서는 서버를 거치지 않는 직접 업로드도 쓸 수 있습니다. `POST /api/v1/uploads/presigned-url`로 받은 주소에 응답의 `headers`를 그대로 넣어 PUT으로 올린 뒤 `POST /api/v1/uploads/complete`에 `fileKey`를 보내면 이미지 주소를 돌려줍니다. 버킷 CORS는 `cors_allowed_origins`의 PUT만 허용합니다.

저장소 `.gitignore`는 `terraform.tfvars`, `*.tfstate*`, `.terraform/`, `backend.hcl`을 제외합니다. 운영 파일을 만들기 전에 `git status`로 비밀값과 상태 파일이 추적되지 않는지 확인합니다.
