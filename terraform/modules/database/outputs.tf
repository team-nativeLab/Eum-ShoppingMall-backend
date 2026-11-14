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

output "db_instance_identifier" {
  description = "RDS 인스턴스 이름(경보용). 꺼져 있으면 null이다."
  value       = one(aws_db_instance.main[*].identifier)
}

output "db_audit_log_group_name" {
  description = "RDS 감사 로그 그룹 이름(경보용). 꺼져 있으면 null이다."
  value       = try(aws_cloudwatch_log_group.db["audit"].name, null)
}

output "redis_cluster_id" {
  description = "Redis 노드 이름(경보용). 노드가 하나인 복제 그룹의 첫 노드다. 꺼져 있으면 null이다."
  value       = var.enabled ? "${var.name}-redis-001" : null
}
