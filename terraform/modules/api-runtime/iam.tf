# ECS가 쓰는 IAM 역할. 업로드 S3에 접근하는 IAM 사용자는 modules/photo-storage/s3.tf에 있다.

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
        compact([var.database_app_secret_arn, var.database_migration_secret_arn]),
        var.redis_secret_arns,
        values(var.application_secret_arns)
      )
    }]
  })
}

# 태스크 역할: 실행 중인 앱 컨테이너가 쓴다. 지금은 EFS를 켰을 때 마운트 권한만 준다.
resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

resource "aws_iam_role_policy" "efs" {
  count = var.enable_efs ? 1 : 0
  name  = "${var.name}-mount-uploads"
  role  = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite"]
      Resource = var.efs_file_system_arn
      Condition = {
        StringEquals = { "elasticfilesystem:AccessPointArn" = var.efs_access_point_arn }
      }
    }]
  })
}

# DB 관리 접속 작업(db_admin)의 태스크 역할(#131). ECS Exec(SSM 세션)에 필요한 권한만 준다.
# DB 계정이나 다른 AWS 권한은 없으며, 접속하는 사람이 자기 DB 계정으로 로그인한다.
resource "aws_iam_role" "db_admin" {
  count              = var.enable_service ? 1 : 0
  name               = "${var.name}-ecs-db-admin"
  assume_role_policy = local.ecs_tasks_assume_role_policy
}

resource "aws_iam_role_policy" "db_admin_exec" {
  count = var.enable_service ? 1 : 0
  name  = "${var.name}-ecs-exec"
  role  = aws_iam_role.db_admin[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ssmmessages:CreateControlChannel",
        "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel",
        "ssmmessages:OpenDataChannel"
      ]
      Resource = "*"
    }]
  })
}
