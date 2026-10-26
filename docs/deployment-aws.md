# AWS 배포 구성

## 구성

`Application Load Balancer → ECS Fargate (private subnets) → RDS MySQL / ElastiCache Redis`로 구성합니다. ALB와 앱 사이에만 애플리케이션 포트(8080)를 열고, DB·Redis는 앱 보안 그룹에서만 접근하게 합니다. HTTPS는 ALB에서 종료하고 HTTP 요청은 HTTPS로 리다이렉트합니다.

Terraform은 VPC(공용·앱·데이터 서브넷 2개 AZ), NAT Gateway 1개, ALB, ECS, ECR, RDS MySQL 8.4, TLS/AUTH Redis, 업로드 이미지용 비공개 S3 버킷과 CloudFront, 암호화된 EFS 업로드 볼륨, CloudWatch 로그 그룹을 만듭니다. 기본 용량은 개발 검증에 맞춘 최소치입니다. `db_multi_az=false`, ECS 1개, Redis 단일 노드는 운영 고가용성 설정이 아닙니다. 운영 전에는 예상 비용을 확인하고 DB Multi-AZ, ECS 2개 이상, Redis 복제/자동 장애 조치를 정해야 합니다. 단일 NAT Gateway는 비용을 줄이는 대신 NAT 장애 시 앱 서브넷의 외부 통신도 영향을 받습니다.

애플리케이션은 업무 시간대를 `Asia/Seoul`로 고정하고 Hibernate JDBC에도 같은 시간대를 지정합니다. API의 `LocalDateTime` 값은 시간대 오프셋을 포함하지 않으므로 한국 현지 시각으로 해석해야 합니다. DB의 `DATETIME(6)` 값도 같은 기준으로 저장됩니다.

ALB의 기본 X-Forwarded-For 처리 모드는 `append`입니다. 앱의 `TRUSTED_PROXY_CIDRS`에는 ALB가 위치한 정확한 공용 서브넷 CIDR만 전달하고, ECS 작업 포트는 ALB 보안 그룹에서만 받습니다. AWS 문서: [ALB forwarded headers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html), [Fargate task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html).

업로드 이미지는 `upload_storage` 변수로 저장소를 고릅니다. 기본값 `local`은 암호화된 EFS에 저장하고 앱이 `/files/**`로 내려줍니다. `s3`로 바꾸면 비공개 S3 버킷(퍼블릭 액세스 차단)에 저장하고 CloudFront(OAC)로만 내보냅니다. 앱은 업로드 전용 IAM 사용자(`terraform output uploads_iam_user`)의 액세스 키로 버킷에 접근합니다. 이 사용자는 버킷의 `products/` 아래만 읽고 쓰고 지울 수 있습니다. 시크릿 키가 Terraform state에 남지 않도록 액세스 키는 콘솔이나 `aws iam create-access-key`로 따로 만들어 Secrets Manager에 넣고, 그 ARN을 `application_secret_arns`의 `UPLOAD_S3_ACCESS_KEY`, `UPLOAD_S3_SECRET_KEY`로 넘깁니다. 키는 주기적으로 새로 만들어 바꾸고 이전 키는 비활성화합니다. 이미 EFS에 있는 이미지는 아래 "업로드 이미지를 S3로 옮기기"를 마친 뒤에 `s3`로 바꿉니다.

앱은 매일 04:30(`UPLOAD_CLEANUP_CRON`)에 상품·배너가 쓰지 않는 이미지 중 올린 지 24시간이 지난 파일을 지웁니다(한 번에 최대 1,000개). 버킷은 버전 관리를 켜 두었으므로 지운 파일도 30일 동안 이전 버전으로 되살릴 수 있습니다.

## 실제 AWS 생성 전에 필요한 것

- AWS 계정의 배포 전용 IAM Identity Center/SSO 권한과 AWS CLI. 액세스 키를 저장소나 채팅에 넣지 않습니다.
- `ap-northeast-2`의 검증 완료된 ACM 인증서와 그 인증서에 포함된 API 도메인 DNS 권한.
- AWS Secrets Manager의 `JWT_SECRET`(강한 랜덤 32바이트 이상), `{"username":"shopping_app","password":"..."}` JSON DB 앱 계정 secret, 실제 프론트 CORS origin, 결제 사용 시 Toss secret.
- Terraform 상태를 보관할 비공개 S3 버킷. 버전 관리, 기본 암호화, 퍼블릭 액세스 차단을 켜고 `backend.hcl.example`을 복사해 실제 버킷명으로 채웁니다. Redis AUTH 값은 Terraform state에도 저장되므로 state 접근 권한을 제한합니다.
- 앱 secret은 기본 AWS 관리형 KMS 키로 암호화하는 구성을 가정합니다. 고객 관리형 KMS 키를 쓰면 ECS 실행 역할에 해당 키의 `kms:Decrypt` 권한을 정확히 추가해야 합니다.
- 현재 앱은 운영에서 `ddl-auto: validate`를 사용합니다. 빈 RDS에 테이블을 만드는 마이그레이션이 먼저 필요합니다(Issue #27). 별도 DB 관리자 계정으로 스키마를 만들고 `shopping_app`에는 앱 구동에 필요한 SELECT/INSERT/UPDATE/DELETE 권한만 부여한 후 `enable_ecs_service=true`로 바꿉니다. Terraform은 이 준비가 끝날 때까지 ECS 서비스를 만들지 않습니다.

Terraform은 리소스를 생성해 과금할 수 있습니다. 아래 `apply`는 비용 검토와 계정/리전 확인 뒤 운영자가 실행합니다. 이 저장소 변경에서는 AWS 리소스를 만들지 않습니다.

## 배포 절차

1. Docker와 Terraform 1.10 이상, AWS CLI를 설치합니다. SSO 프로파일로 로그인하고 `aws sts get-caller-identity`로 계정을 확인합니다.
2. `infra/aws/terraform.tfvars.example`을 `infra/aws/terraform.tfvars`로 복사하고 인증서 ARN, 도메인 origin, secret ARN을 채웁니다. 실제 secret 값은 AWS Secrets Manager에 직접 넣습니다. `terraform.tfvars`는 Git에 추가하지 않습니다.
3. `infra/aws/backend.hcl.example`을 `backend.hcl`로 복사해 상태 버킷명을 채운 뒤 실행합니다.

   ```sh
   cd infra/aws
   terraform init -backend-config=backend.hcl
   terraform validate
   terraform plan
   ```

4. 첫 배포에서는 ECR 저장소만 먼저 만듭니다. 이 명령은 AWS 계정에 리소스를 생성합니다.

   ```sh
   terraform apply -target=aws_ecr_repository.api
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

6. `terraform.tfvars`의 `image_tag`를 같은 SHA로 설정합니다. 셸에서 `TF_VAR_redis_auth_token`을 안전하게 주입하고 다시 `terraform plan`을 검토한 뒤에만 `terraform apply`를 실행합니다. Terraform이 Secret Manager에 Redis AUTH 값을 저장하고 ElastiCache/ECS에 전달합니다.
7. RDS가 생성되면 AWS가 관리하는 `rds_master_secret_arn`으로 승인된 비공개 DB 접속 경로에서 스키마와 제한된 앱 DB 계정을 준비합니다. DB 앱 secret에는 `username`, `password` 키를 넣습니다. `shopping_app`에는 `shopping` 데이터베이스의 `SELECT, INSERT, UPDATE, DELETE` 권한만 주고, 관리자 계정은 앱에 전달하지 않습니다. SQL 마이그레이션(Issue #27) 없이 앱 서비스를 켜지 않습니다.
8. `terraform.tfvars`에서 `enable_ecs_service=true`로 바꾸고 `terraform plan`을 검토한 뒤 `terraform apply`를 실행합니다.
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
5. 문제가 없으면 이후 변경에서 EFS 볼륨과 마운트 설정을 지웁니다.

관리자 화면은 지금처럼 `POST /api/v1/uploads`(multipart)로 올려도 되고, S3에서는 서버를 거치지 않는 직접 업로드도 쓸 수 있습니다. `POST /api/v1/uploads/presigned-url`로 받은 주소에 응답의 `headers`를 그대로 넣어 PUT으로 올린 뒤 `POST /api/v1/uploads/complete`에 `fileKey`를 보내면 이미지 주소를 돌려줍니다. 버킷 CORS는 `cors_allowed_origins`의 PUT만 허용합니다.

저장소 `.gitignore`는 `terraform.tfvars`, `*.tfstate*`, `.terraform/`, `backend.hcl`을 제외합니다. 운영 파일을 만들기 전에 `git status`로 비밀값과 상태 파일이 추적되지 않는지 확인합니다.
