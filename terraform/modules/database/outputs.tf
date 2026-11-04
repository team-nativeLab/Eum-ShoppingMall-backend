output "db_address" {
  description = "RDS 접속 주소. 꺼져 있으면 null이다."
  value       = one(aws_db_instance.main[*].address)
}

output "db_master_secret_arn" {
  description = "AWS가 관리하는 RDS 마스터 계정 시크릿의 ARN. 꺼져 있으면 null이다."
  value       = one(aws_db_instance.main[*].master_user_secret[0].secret_arn)
}

output "redis_endpoint" {
  description = "Redis 접속 주소. 꺼져 있으면 null이다."
  value       = one(aws_elasticache_replication_group.main[*].primary_endpoint_address)
}

output "redis_secret_arns" {
  description = "Redis AUTH 토큰 시크릿 ARN. 꺼져 있으면 빈 목록이다."
  value       = aws_secretsmanager_secret.redis[*].arn
}
