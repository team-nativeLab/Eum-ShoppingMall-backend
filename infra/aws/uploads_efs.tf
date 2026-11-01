# upload_storage = "local"일 때 업로드 이미지를 저장하는 EFS. ECS 태스크는 액세스 포인트를 거쳐 /uploads만 쓴다.
# S3로 옮긴 뒤에는 지울 수 있다(docs/deployment-aws.md의 "업로드 이미지를 S3로 옮기기").

resource "aws_efs_file_system" "uploads" {
  encrypted        = true
  performance_mode = "generalPurpose"
  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }
}

resource "aws_efs_access_point" "uploads" {
  file_system_id = aws_efs_file_system.uploads.id
  posix_user {
    uid = local.app_posix_id
    gid = local.app_posix_id
  }
  root_directory {
    path = "/uploads"
    creation_info {
      owner_uid   = local.app_posix_id
      owner_gid   = local.app_posix_id
      permissions = "0750"
    }
  }
}

resource "aws_efs_mount_target" "uploads" {
  count           = 2
  file_system_id  = aws_efs_file_system.uploads.id
  subnet_id       = aws_subnet.app[count.index].id
  security_groups = [aws_security_group.efs.id]
}
