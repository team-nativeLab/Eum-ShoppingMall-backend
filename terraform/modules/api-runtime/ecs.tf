# API를 실행하는 ECS Fargate와 로그 그룹.

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = var.name
}

# 태스크 정의와 서비스는 enable_service(스택의 enable_ecs_service)를 켜야 만든다. 제한된 DB 계정과 스키마가 먼저 준비돼야 한다.
# 이때 RDS, Redis, ALB, NAT Gateway가 모두 있는지는 stacks/app/variables.tf의 enable_ecs_service 검증이 확인한다.
resource "aws_ecs_task_definition" "api" {
  count                    = var.enable_service ? 1 : 0
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  dynamic "volume" {
    for_each = var.enable_efs ? [1] : []
    content {
      name = "uploads"
      efs_volume_configuration {
        file_system_id     = var.efs_file_system_id
        transit_encryption = "ENABLED"
        authorization_config {
          access_point_id = var.efs_access_point_id
          iam             = "ENABLED"
        }
      }
    }
  }

  container_definitions = jsonencode([{
    name         = local.api_container_name
    image        = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = var.api_port, protocol = "tcp" }]
    environment = concat([
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      # 스키마는 migrate 작업만 바꾼다(#131). API는 DML 전용 앱 계정으로 뜨고 Hibernate가 스키마가 맞는지만 확인한다.
      { name = "SPRING_FLYWAY_ENABLED", value = "false" },
      # RDS 인증서는 이미지에 넣은 RDS CA 신뢰 저장소로만 검증한다(Dockerfile, #133). 저장소 비밀번호는 비밀이 아니다.
      { name = "DB_URL", value = "jdbc:mysql://${var.db_address}:3306/${var.db_name}?${local.db_url_options}" },
    ], local.shared_environment)
    # 비밀 값은 environment에 넣지 않는다. ECS가 Secrets Manager에서 읽어 환경변수로 넣는다.
    secrets = concat([
      { name = "DB_USERNAME", valueFrom = "${var.database_app_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.database_app_secret_arn}:password::" },
      { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = var.redis_secret_arns[0] }
    ], local.app_secrets)
    mountPoints = var.enable_efs ? [{ sourceVolume = "uploads", containerPath = local.uploads_mount_path, readOnly = false }] : []
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "api"
      }
    }
  }])
  depends_on = [aws_iam_role_policy_attachment.execution, aws_iam_role_policy.secrets, aws_iam_role_policy.efs]

  lifecycle {
    # 두 시크릿은 username·password 키 형식이 같아 RDS 마스터 시크릿을 실수로 넣기 쉽다. 넣으면 앱이 마스터 계정으로 접속한다(#131).
    precondition {
      condition = var.database_master_secret_arn == null || !contains(
        [var.database_app_secret_arn, var.database_migration_secret_arn], var.database_master_secret_arn
      )
      error_message = "database_app_secret_arn·database_migration_secret_arn에 RDS 마스터 시크릿(rds_master_secret_arn)을 넣으면 안 됩니다."
    }
  }
}

# DB 마이그레이션만 하고 끝나는 일회성 작업(#131). 배포할 때 새 이미지로 이 작업을 먼저 돌리고(aws ecs run-task),
# 종료 코드 0으로 끝나면 API 서비스를 새 이미지로 바꾼다. DDL 권한이 있는 마이그레이션 전용 계정은 이 작업만 받는다.
resource "aws_ecs_task_definition" "migrate" {
  count                    = var.enable_service ? 1 : 0
  family                   = "${var.name}-migrate"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn

  container_definitions = jsonencode([{
    name      = "migrate"
    image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential = true
    environment = concat([
      # Flyway를 적용하고 Hibernate가 스키마를 검증한 뒤 끝난다(application-migrate.yaml, MigrationOnlyRunner).
      { name = "SPRING_PROFILES_ACTIVE", value = "prod,migrate" },
      { name = "DB_URL", value = "jdbc:mysql://${var.db_address}:3306/${var.db_name}?${local.db_url_options}" },
    ], local.shared_environment)
    secrets = concat([
      { name = "DB_USERNAME", valueFrom = "${var.database_migration_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.database_migration_secret_arn}:password::" },
      { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = var.redis_secret_arns[0] }
    ], local.app_secrets)
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "migrate"
      }
    }
  }])
  depends_on = [aws_iam_role_policy_attachment.execution, aws_iam_role_policy.secrets]
}

# DB 관리 접속용 일회성 작업(#131). DB 보안 그룹이나 퍼블릭 접근을 열지 않고, 이 작업을 ECS Exec로 띄워
# SSM 포트 포워딩으로 RDS에 붙는다. 아무것도 하지 않고 1시간 뒤 끝난다. 방법은 docs/deployment-aws.md의 "DB 관리 접속"을 따른다.
resource "aws_ecs_task_definition" "db_admin" {
  count                    = var.enable_service ? 1 : 0
  family                   = "${var.name}-db-admin"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.db_admin[0].arn

  container_definitions = jsonencode([{
    name            = "db-admin"
    image           = "public.ecr.aws/amazonlinux/amazonlinux:2023"
    essential       = true
    command         = ["sleep", "3600"]
    linuxParameters = { initProcessEnabled = true }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "db-admin"
      }
    }
  }])
  depends_on = [aws_iam_role_policy_attachment.execution, aws_iam_role_policy.db_admin_exec]
}

resource "aws_ecs_service" "api" {
  count                              = var.enable_service ? 1 : 0
  name                               = var.name
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.api[0].arn
  desired_count                      = var.desired_count
  launch_type                        = "FARGATE"
  enable_execute_command             = false
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.app_security_group_id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.api[0].arn
    container_name   = local.api_container_name
    container_port   = var.api_port
  }
  depends_on = [aws_lb_listener.https]
}
