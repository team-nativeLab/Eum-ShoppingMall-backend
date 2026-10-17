# AWS 배포 구성

## 구성

`Application Load Balancer → ECS Fargate (private subnets) → RDS MySQL / ElastiCache Redis`로 구성합니다. ALB와 앱 사이에만 애플리케이션 포트(8080)를 열고, DB·Redis는 앱 보안 그룹에서만 접근하게 합니다. HTTPS는 ALB에서 종료하고 HTTP 요청은 HTTPS로 리다이렉트합니다.

Terraform은 VPC(공용·앱·데이터 서브넷 2개 AZ), NAT Gateway 1개, ALB, ECS, ECR, RDS MySQL 8.4, TLS/AUTH Redis, 암호화된 EFS 업로드 볼륨, CloudWatch 로그 그룹을 만듭니다. 기본 용량은 개발 검증에 맞춘 최소치입니다. `db_multi_az=false`, ECS 1개, Redis 단일 노드는 운영 고가용성 설정이 아닙니다. 운영 전에는 예상 비용을 확인하고 DB Multi-AZ, ECS 2개 이상, Redis 복제/자동 장애 조치를 정해야 합니다. 단일 NAT Gateway는 비용을 줄이는 대신 NAT 장애 시 앱 서브넷의 외부 통신도 영향을 받습니다.

ALB의 기본 X-Forwarded-For 처리 모드는 `append`입니다. 앱의 `TRUSTED_PROXY_CIDRS`에는 ALB가 위치한 정확한 공용 서브넷 CIDR만 전달하고, ECS 작업 포트는 ALB 보안 그룹에서만 받습니다. AWS 문서: [ALB forwarded headers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html), [Fargate task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html).

업로드는 현재 로컬 파일 저장 구현이므로 Fargate의 임시 디스크에 두지 않고 암호화된 EFS에 마운트합니다. 이미지 파일을 S3에 저장하는 Issue #13이 반영되면 EFS 대신 S3로 옮기는 것이 다음 단계입니다.

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

저장소 `.gitignore`는 `terraform.tfvars`, `*.tfstate*`, `.terraform/`, `backend.hcl`을 제외합니다. 운영 파일을 만들기 전에 `git status`로 비밀값과 상태 파일이 추적되지 않는지 확인합니다.
