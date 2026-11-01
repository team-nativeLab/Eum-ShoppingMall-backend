locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  app_secrets = [
    for name, arn in var.application_secret_arns : {
      name      = name
      valueFrom = arn
    }
  ]

  api_port = 8080

  api_container_name = "api"

  app_posix_id = 10001

  uploads_mount_path = "/app/uploads"

  ecs_tasks_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}
