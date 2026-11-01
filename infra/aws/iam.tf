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
