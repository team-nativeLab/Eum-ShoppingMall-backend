# ECS가 쓰는 IAM 역할. 업로드 S3에 접근하는 IAM 사용자는 uploads_s3.tf에 있다.

# 실행 역할: ECS가 태스크를 띄울 때 쓴다(이미지 받기, 로그 쓰기, 시크릿 읽기).
resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "secrets" {
  name = "${var.name}-read-runtime-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = concat(
        [var.database_app_secret_arn, aws_secretsmanager_secret.redis.arn],
        values(var.application_secret_arns)
      )
    }]
  })
}

# 태스크 역할: 실행 중인 앱 컨테이너가 쓴다. 지금은 EFS 마운트 권한만 준다.
resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

resource "aws_iam_role_policy" "efs" {
  name = "${var.name}-mount-uploads"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite"]
      Resource = aws_efs_file_system.uploads.arn
      Condition = {
        StringEquals = { "elasticfilesystem:AccessPointArn" = aws_efs_access_point.uploads.arn }
      }
    }]
  })
}
