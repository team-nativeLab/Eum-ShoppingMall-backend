output "ecr_repository_url" {
  description = "이미지를 푸시할 ECR 저장소 주소"
  value       = aws_ecr_repository.api.repository_url
}

output "alb_dns_name" {
  description = "ALB DNS 이름. ALB를 끄면 null이다."
  value       = one(aws_lb.api[*].dns_name)
}

output "ecs_cluster_name" {
  description = "ECS 클러스터 이름. migrate·db-admin 작업을 aws ecs run-task로 띄울 때 쓴다."
  value       = aws_ecs_cluster.main.name
}

output "migrate_task_family" {
  description = "DB 마이그레이션 작업의 태스크 정의 이름(#131). 서비스를 끄면 null이다."
  value       = one(aws_ecs_task_definition.migrate[*].family)
}

output "db_admin_task_family" {
  description = "DB 관리 접속 작업의 태스크 정의 이름(#131). 서비스를 끄면 null이다."
  value       = one(aws_ecs_task_definition.db_admin[*].family)
}
