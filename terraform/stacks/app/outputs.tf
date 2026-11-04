output "api_url" {
  description = "공개 API 주소. api_domain의 DNS를 ALB로 연결하고 이 도메인이 포함된 인증서를 쓴다. ALB를 끄면 null이다."
  value       = var.enable_alb ? "https://${var.api_domain}" : null
}

output "alb_dns_name" {
  description = "ALB DNS 이름. api_domain이 가리켜야 하는 대상. ALB를 끄면 null이다."
  value       = module.api_runtime.alb_dns_name
}

output "ecr_repository_url" {
  description = "이미지를 푸시할 ECR 저장소 주소"
  value       = module.api_runtime.ecr_repository_url
}

output "rds_endpoint" {
  description = "RDS 접속 주소. 데이터 계층을 끄면 null이다."
  value       = module.database.db_address
}

output "redis_endpoint" {
  description = "Redis 접속 주소. 데이터 계층을 끄면 null이다."
  value       = module.database.redis_endpoint
}

output "rds_master_secret_arn" {
  description = "AWS가 관리하는 RDS 마스터 계정 시크릿의 ARN. 데이터 계층을 끄면 null이다."
  value       = module.database.db_master_secret_arn
}

output "uploads_bucket" {
  description = "업로드 이미지 S3 버킷 이름"
  value       = module.photo_storage.bucket
}

output "uploads_cdn_url" {
  description = "업로드 이미지의 기본 주소(UPLOAD_CDN_BASE_URL)"
  value       = module.photo_storage.cdn_url
}

output "uploads_iam_user" {
  description = "업로드용 IAM 사용자. 이 사용자의 액세스 키를 만들어 Secrets Manager에 넣고 application_secret_arns에 추가한다."
  value       = module.photo_storage.iam_user_name
}
