resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = var.name
}

resource "aws_ecs_task_definition" "api" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  volume {
    name = "uploads"
    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.uploads.id
      transit_encryption = "ENABLED"
      authorization_config {
        access_point_id = aws_efs_access_point.uploads.id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([{
    name         = "api"
    image        = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_URL", value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.db_name}?sslMode=VERIFY_IDENTITY" },
      { name = "REDIS_HOST", value = aws_elasticache_replication_group.main.primary_endpoint_address },
      { name = "REDIS_PORT", value = "6379" },
      { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
      { name = "TRUSTED_PROXY_CIDRS", value = join(",", var.public_subnet_cidrs) },
      { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
      { name = "REFRESH_COOKIE_SAME_SITE", value = var.refresh_cookie_same_site },
      { name = "UPLOAD_DIR", value = "/app/uploads" },
      { name = "UPLOAD_STORAGE", value = var.upload_storage },
      { name = "UPLOAD_S3_BUCKET", value = aws_s3_bucket.uploads.bucket },
      { name = "UPLOAD_CDN_BASE_URL", value = "https://${aws_cloudfront_distribution.uploads.domain_name}" },
      { name = "AWS_REGION", value = var.aws_region }
    ]
    secrets = concat([
      { name = "DB_USERNAME", valueFrom = "${var.database_app_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.database_app_secret_arn}:password::" },
      { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = aws_secretsmanager_secret.redis.arn }
    ], local.app_secrets)
    mountPoints = [{ sourceVolume = "uploads", containerPath = "/app/uploads", readOnly = false }]
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
  task_definition                    = aws_ecs_task_definition.api.arn
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
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https, aws_efs_mount_target.uploads]
}
