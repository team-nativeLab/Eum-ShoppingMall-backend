output "state_bucket" {
  description = "stacks/app/backend.hcl의 bucket에 넣을 이름"
  value       = aws_s3_bucket.state.bucket
}
