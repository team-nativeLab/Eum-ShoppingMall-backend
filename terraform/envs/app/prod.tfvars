# stacks/app 운영 환경 값. 실제 값으로 바꿔 커밋한다. ARN과 설정만 적고 비밀 값은 넣지 않는다.
#
# 사용법:
#   cd terraform/stacks/app
#   terraform init -backend-config=backend.hcl
#   terraform plan -var-file=../../envs/app/prod.tfvars
aws_region           = "ap-northeast-2"
name                 = "eum-shopping"
certificate_arn      = "arn:aws:acm:ap-northeast-2:123456789012:certificate/replace-me"
api_domain           = "api.example.com"
cors_allowed_origins = "https://shop.example.com"
image_tag            = "replace-with-immutable-git-sha"
# 로그인 갱신이 동작하려면 웹 프론트가 API와 같은 등록 도메인을 써야 한다(shop.example.com + api.example.com).
# *.vercel.app 프론트는 어떤 API 도메인과도 다른 사이트다. docs/deployment-proxy.md를 참고한다.
refresh_cookie_same_site = "Strict"

# 쓰지 않아도 요금이 나오는 리소스. 모두 false면 고정 비용이 없다.
# 켜는 순서와 구성별 비용은 docs/deployment-aws.md의 "비용과 구성 단계"를 참고한다.
enable_data_layer  = false
enable_nat_gateway = false
enable_alb         = false
enable_efs         = false
enable_ecs_service = false

# 월 예산과 알림 수신자. 팀에서 확정한 값으로 바꿔야 plan이 통과한다.
budget_limit_usd    = 0
budget_alert_emails = ["replace-me@example.com"]

# 값은 AWS Secrets Manager에 먼저 만들고 여기에는 ARN만 적는다. 비밀 값을 직접 넣지 않는다.
application_secret_arns = {
  JWT_SECRET      = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/jwt-replace"
  TOSS_SECRET_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/toss-replace"
  # upload_storage = "s3"이면 필수: uploads_iam_user 출력에 나오는 사용자의 액세스 키
  # UPLOAD_S3_ACCESS_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/s3-access-key-replace"
  # UPLOAD_S3_SECRET_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/s3-secret-key-replace"
  # 인증번호 로봇 확인을 켜면(app_environment의 CAPTCHA_ENABLED = "true") 필수.
  # 이때 app_environment에 CAPTCHA_ALLOWED_HOSTNAMES = "shop.example.com"(위젯을 푸는 프론트 도메인)도 넣는다.
  # CAPTCHA_SECRET_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/captcha-replace"
}
database_app_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/db-app-replace"
# 마이그레이션 전용 계정(DDL 권한). 일회성 migrate 작업만 받는다(#131). enable_ecs_service를 켜면 필수다.
database_migration_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:eum-shopping/db-migrate-replace"

# 담당자 알림 메일에 필요한 값. MAIL_USERNAME, MAIL_PASSWORD는 application_secret_arns에 넣는다.
# app_environment = {
#   ALERT_EMAILS = "담당자 이메일(쉼표로 구분)"
#   MAIL_HOST    = "SMTP 서버 주소"
#   MAIL_FROM    = "보내는 사람 주소"
# }

# 이 파일에 넣지 않고 실행할 때 안전한 셸이나 CI 시크릿 저장소에서 넘긴다.
# export TF_VAR_redis_auth_token="<16~128자 Redis AUTH 토큰>"

# 운영에서는 비용을 검토한 뒤 Multi-AZ를 켜고 ECS·Redis 규모를 늘린다.
db_multi_az = false

# 기존 EFS 이미지를 S3로 옮길 때까지 local로 두고(docs/deployment-aws.md), 옮긴 뒤 s3로 바꾼다.
upload_storage = "local"
