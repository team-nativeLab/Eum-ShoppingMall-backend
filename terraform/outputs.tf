output "api_url" {
  description = "Public API URL. Point api_domain DNS at the ALB and use a certificate covering it."
  value       = "https://${var.api_domain}"
}

output "alb_dns_name" {
  value = aws_lb.api.dns_name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "rds_master_secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "uploads_bucket" {
  value = aws_s3_bucket.uploads.bucket
}

output "uploads_cdn_url" {
  description = "Base URL of uploaded images (UPLOAD_CDN_BASE_URL)."
  value       = "https://${aws_cloudfront_distribution.uploads.domain_name}"
}

output "uploads_iam_user" {
  description = "Create this user's access key, store it in Secrets Manager, and add it to application_secret_arns."
  value       = aws_iam_user.uploads.name
}
