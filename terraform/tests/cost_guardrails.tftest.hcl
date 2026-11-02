# AWS 없이 mock provider로 plan만 돌려 구성별로 만들어지는 리소스를 확인한다.
mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["ap-northeast-2a", "ap-northeast-2c"]
    }
  }
}

variables {
  certificate_arn      = "arn:aws:acm:ap-northeast-2:123456789012:certificate/test"
  api_domain           = "api.eum-shopping.test"
  image_tag            = "testsha"
  cors_allowed_origins = "https://shop.eum-shopping.test"
  application_secret_arns = {
    JWT_SECRET = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:jwt"
  }
  database_app_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:db-app"
  redis_auth_token        = "test-redis-token-1234"
  budget_limit_usd        = 200
  budget_alert_emails     = ["ops@eum-shopping.test", "dev@eum-shopping.test"]
}

run "default_has_no_always_billed_resources" {
  command = plan

  assert {
    condition = (
      length(aws_nat_gateway.main) + length(aws_eip.nat) + length(aws_lb.api) +
      length(aws_db_instance.main) + length(aws_elasticache_replication_group.main) +
      length(aws_secretsmanager_secret.redis) + length(aws_efs_file_system.uploads) +
      length(aws_ecs_task_definition.api) + length(aws_ecs_service.api)
    ) == 0
    error_message = "기본 구성에 쓰지 않아도 요금이 나오는 리소스가 있습니다."
  }

  assert {
    condition     = aws_budgets_budget.monthly.limit_amount == "200"
    error_message = "기본 구성에도 월 예산 경보가 있어야 합니다."
  }
}

run "data_layer_creates_only_rds_and_redis" {
  command = plan

  variables {
    enable_data_layer = true
  }

  assert {
    condition     = length(aws_db_instance.main) == 1 && length(aws_elasticache_replication_group.main) == 1
    error_message = "enable_data_layer를 켜면 RDS와 Redis가 있어야 합니다."
  }

  assert {
    condition     = length(aws_nat_gateway.main) + length(aws_lb.api) + length(aws_efs_file_system.uploads) == 0
    error_message = "enable_data_layer만 켰는데 NAT, ALB, EFS가 만들어집니다."
  }
}

run "full_stack_creates_everything" {
  command = plan

  variables {
    enable_data_layer  = true
    enable_nat_gateway = true
    enable_alb         = true
    enable_efs         = true
    enable_ecs_service = true
  }

  assert {
    condition = alltrue([
      length(aws_nat_gateway.main) == 1,
      length(aws_route_table_association.app) == 2,
      length(aws_lb.api) == 1,
      length(aws_efs_mount_target.uploads) == 2,
      length(aws_ecs_service.api) == 1,
      length(aws_ecs_task_definition.api[0].volume) == 1,
    ])
    error_message = "모두 켰는데 빠진 리소스가 있습니다."
  }
}

run "s3_storage_runs_without_efs" {
  command = plan

  variables {
    enable_data_layer  = true
    enable_nat_gateway = true
    enable_alb         = true
    enable_ecs_service = true
    upload_storage     = "s3"
    application_secret_arns = {
      JWT_SECRET           = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:jwt"
      UPLOAD_S3_ACCESS_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:s3-access"
      UPLOAD_S3_SECRET_KEY = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:s3-secret"
    }
  }

  assert {
    condition     = length(aws_efs_file_system.uploads) == 0 && length(aws_ecs_task_definition.api[0].volume) == 0
    error_message = "enable_efs를 끄면 EFS와 태스크 정의의 볼륨이 없어야 합니다."
  }
}

run "ecs_service_requires_what_it_runs_on" {
  command = plan

  variables {
    enable_ecs_service = true
    enable_efs         = true
  }

  expect_failures = [var.enable_ecs_service]
}

run "local_storage_requires_efs" {
  command = plan

  variables {
    enable_data_layer  = true
    enable_nat_gateway = true
    enable_alb         = true
    enable_ecs_service = true
  }

  expect_failures = [var.enable_ecs_service]
}

run "budget_alerts_every_recipient_at_each_threshold" {
  command = plan

  assert {
    condition     = length(aws_budgets_budget.monthly.notification) == 4
    error_message = "실제 비용 알림 3개와 예측 비용 알림 1개가 있어야 합니다."
  }

  assert {
    condition = alltrue([
      for notification in aws_budgets_budget.monthly.notification :
      contains(notification.subscriber_email_addresses, "ops@eum-shopping.test") &&
      contains(notification.subscriber_email_addresses, "dev@eum-shopping.test")
    ])
    error_message = "모든 알림이 지정한 수신자에게 가야 합니다."
  }

  assert {
    condition = anytrue([
      for notification in aws_budgets_budget.monthly.notification :
      notification.notification_type == "FORECASTED" && notification.threshold == 100
    ])
    error_message = "예측 비용 100% 알림이 있어야 합니다."
  }
}

run "budget_rejects_example_values" {
  command = plan

  variables {
    budget_limit_usd    = 0
    budget_alert_emails = ["replace-me@example.com"]
  }

  expect_failures = [var.budget_limit_usd, var.budget_alert_emails]
}

run "budget_rejects_too_many_notifications" {
  command = plan

  variables {
    budget_alert_thresholds = [20, 40, 60, 80, 100]
  }

  expect_failures = [var.budget_alert_thresholds]
}
