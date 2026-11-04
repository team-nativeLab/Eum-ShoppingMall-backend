output "bucket" {
  description = "업로드 이미지 S3 버킷 이름"
  value       = aws_s3_bucket.uploads.bucket
}

output "cdn_url" {
  description = "업로드 이미지의 기본 주소(UPLOAD_CDN_BASE_URL)"
  value       = "https://${aws_cloudfront_distribution.uploads.domain_name}"
}

output "iam_user_name" {
  description = "업로드용 IAM 사용자 이름"
  value       = aws_iam_user.uploads.name
}

output "efs_file_system_id" {
  value = one(aws_efs_file_system.uploads[*].id)
}

output "efs_file_system_arn" {
  value = one(aws_efs_file_system.uploads[*].arn)
}

# ECS가 볼륨을 붙이기 전에 마운트 타깃이 있어야 하므로, 마운트 타깃이 생긴 뒤에 이 값을 넘긴다.
output "efs_access_point_id" {
  value      = one(aws_efs_access_point.uploads[*].id)
  depends_on = [aws_efs_mount_target.uploads]
}

output "efs_access_point_arn" {
  value = one(aws_efs_access_point.uploads[*].arn)
}
