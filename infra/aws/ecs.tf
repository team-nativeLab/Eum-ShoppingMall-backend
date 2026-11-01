# API를 실행하는 ECS Fargate와 로그 그룹.

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = var.name
}

# 태스크 정의와 서비스는 enable_ecs_service를 켜야 만든다. 제한된 DB 계정과 스키마가 먼저 준비돼야 한다.
# 이때 RDS, Redis, ALB, NAT Gateway가 모두 있는지는 variables.tf의 enable_ecs_service 검증이 확인한다.
resource "aws_ecs_task_definition" "api" {
  count                    = var.enable_ecs_service ? 1 : 0
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
        file_system_id     = aws_efs_file_system.uploads[0].id
        transit_encryption = "ENABLED"
        authorization_config {
          access_point_id = aws_efs_access_point.uploads[0].id
          iam             = "ENABLED"
        }
      }
    }
  }

  container_definitions = jsonencode([{
    name         = local.api_container_name
    image        = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = local.api_port, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_URL", value = "jdbc:mysql://${aws_db_instance.main[0].address}:3306/${var.db_name}?sslMode=VERIFY_IDENTITY" },
      { name = "REDIS_HOST", value = aws_elasticache_replication_group.main[0].primary_endpoint_address },
      { name = "REDIS_PORT", value = "6379" },
      { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
      # ALB가 있는 공용 서브넷만 프록시로 믿는다.
      { name = "TRUSTED_PROXY_CIDRS", value = join(",", var.public_subnet_cidrs) },
      { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
      { name = "REFRESH_COOKIE_SAME_SITE", value = var.refresh_cookie_same_site },
      { name = "UPLOAD_DIR", value = local.uploads_mount_path },
      { name = "UPLOAD_STORAGE", value = var.upload_storage },
      { name = "UPLOAD_S3_BUCKET", value = aws_s3_bucket.uploads.bucket },
      { name = "UPLOAD_CDN_BASE_URL", value = "https://${aws_cloudfront_distribution.uploads.domain_name}" },
      { name = "AWS_REGION", value = var.aws_region }
    ]
    # 비밀 값은 environment에 넣지 않는다. ECS가 Secrets Manager에서 읽어 환경변수로 넣는다.
    secrets = concat([
      { name = "DB_USERNAME", valueFrom = "${var.database_app_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.database_app_secret_arn}:password::" },
      { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = aws_secretsmanager_secret.redis[0].arn }
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
}

resource "aws_ecs_service" "api" {
  count                              = var.enable_ecs_service ? 1 : 0
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
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.api[0].arn
    container_name   = local.api_container_name
    container_port   = local.api_port
  }
  depends_on = [aws_lb_listener.https, aws_efs_mount_target.uploads]
}
