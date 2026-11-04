output "ecr_repository_url" {
  description = "이미지를 푸시할 ECR 저장소 주소"
  value       = aws_ecr_repository.api.repository_url
}

output "alb_dns_name" {
  description = "ALB DNS 이름. ALB를 끄면 null이다."
  value       = one(aws_lb.api[*].dns_name)
}
