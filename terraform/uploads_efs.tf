# upload_storage = "local"일 때 업로드 이미지를 저장하는 EFS. enable_efs를 켜야 만든다.
# ECS 태스크는 액세스 포인트를 거쳐 /uploads만 쓴다.
# S3로 옮긴 뒤에는 enable_efs를 끈다(docs/deployment-aws.md의 "업로드 이미지를 S3로 옮기기"). 파일도 함께 지워진다.

resource "aws_efs_file_system" "uploads" {
  count            = var.enable_efs ? 1 : 0
  encrypted        = true
  performance_mode = "generalPurpose"
  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }
}

resource "aws_efs_access_point" "uploads" {
  count          = var.enable_efs ? 1 : 0
  file_system_id = aws_efs_file_system.uploads[0].id
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
  count           = var.enable_efs ? 2 : 0
  file_system_id  = aws_efs_file_system.uploads[0].id
  subnet_id       = aws_subnet.app[count.index].id
  security_groups = [aws_security_group.efs.id]
}
