# AWS 없이 mock provider로 plan만 돌린다.
# 스택 전체로는 변수 검증과 조립을 보고, 켜고 끌 때 만들어지는 리소스는 모듈마다 따로 본다.
# terraform test는 하위 모듈의 리소스를 직접 볼 수 없기 때문이다.
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

# ---- 스택 ----

run "stack_default_plans" {
  command = plan
}

run "stack_with_everything_plans" {
  command = plan

  variables {
    enable_data_layer  = true
    enable_nat_gateway = true
    enable_alb         = true
    enable_efs         = true
    enable_ecs_service = true
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

run "admin_origins_must_be_allowed_origins" {
  command = plan

  variables {
    cors_admin_origins = "https://admin.eum-shopping.test"
  }

  expect_failures = [var.cors_admin_origins]
}

run "admin_origins_accepts_listed_origin" {
  command = plan

  variables {
    cors_allowed_origins = "https://shop.eum-shopping.test, https://admin.eum-shopping.test"
    cors_admin_origins   = "https://admin.eum-shopping.test"
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

# ---- 모듈: 기본값으로는 쓰지 않아도 요금이 나오는 리소스를 만들지 않는다 ----

run "network_without_nat" {
  command = plan

  module {
    source = "../../modules/network"
  }

  variables {
    name                = "eum-test"
    vpc_cidr            = "10.20.0.0/16"
    public_subnet_cidrs = ["10.20.0.0/24", "10.20.1.0/24"]
    app_subnet_cidrs    = ["10.20.10.0/24", "10.20.11.0/24"]
    data_subnet_cidrs   = ["10.20.20.0/24", "10.20.21.0/24"]
    api_port            = 8080
  }

  assert {
    condition     = length(aws_nat_gateway.main) + length(aws_eip.nat) + length(aws_route_table.app) == 0
    error_message = "기본값으로 NAT Gateway를 만들면 안 됩니다."
  }
}

run "network_with_nat" {
  command = plan

  module {
    source = "../../modules/network"
  }

  variables {
    name                = "eum-test"
    vpc_cidr            = "10.20.0.0/16"
    public_subnet_cidrs = ["10.20.0.0/24", "10.20.1.0/24"]
    app_subnet_cidrs    = ["10.20.10.0/24", "10.20.11.0/24"]
    data_subnet_cidrs   = ["10.20.20.0/24", "10.20.21.0/24"]
    api_port            = 8080
    enable_nat_gateway  = true
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 1 && length(aws_route_table_association.app) == 2
    error_message = "NAT Gateway를 켜면 앱 서브넷 두 곳이 NAT Gateway로 나가야 합니다."
  }
}

run "database_off" {
  command = plan

  module {
    source = "../../modules/database"
  }

  variables {
    name                       = "eum-test"
    subnet_ids                 = ["subnet-a", "subnet-b"]
    database_security_group_id = "sg-db"
    redis_security_group_id    = "sg-redis"
    db_name                    = "shopping"
    db_username                = "eum_admin"
    db_instance_class          = "db.t4g.micro"
    db_multi_az                = false
    redis_node_type            = "cache.t4g.micro"
  }

  assert {
    condition = (
      length(aws_db_instance.main) + length(aws_elasticache_replication_group.main) +
      length(aws_secretsmanager_secret.redis) + length(aws_db_parameter_group.main)
    ) == 0
    error_message = "기본값으로 RDS, Redis, 시크릿을 만들면 안 됩니다."
  }
}

run "database_enabled" {
  command = plan

  module {
    source = "../../modules/database"
  }

  variables {
    name                       = "eum-test"
    enabled                    = true
    subnet_ids                 = ["subnet-a", "subnet-b"]
    database_security_group_id = "sg-db"
    redis_security_group_id    = "sg-redis"
    db_name                    = "shopping"
    db_username                = "eum_admin"
    db_instance_class          = "db.t4g.micro"
    db_multi_az                = false
    redis_node_type            = "cache.t4g.micro"
  }

  assert {
    condition     = length(aws_db_instance.main) == 1 && length(aws_elasticache_replication_group.main) == 1
    error_message = "켜면 RDS와 Redis가 있어야 합니다."
  }

  assert {
    condition = alltrue([
      aws_db_instance.main[0].parameter_group_name == aws_db_parameter_group.main[0].name,
      contains([for p in aws_db_parameter_group.main[0].parameter : "${p.name}=${p.value}"], "require_secure_transport=1"),
    ])
    error_message = "RDS는 TLS 접속만 받아야 합니다(require_secure_transport=1, #133)."
  }
}

run "photo_storage_without_efs" {
  command = plan

  module {
    source = "../../modules/photo-storage"
  }

  variables {
    name                  = "eum-test"
    app_subnet_ids        = ["subnet-a", "subnet-b"]
    efs_security_group_id = "sg-efs"
    app_posix_id          = 10001
  }

  assert {
    condition     = length(aws_efs_file_system.uploads) + length(aws_efs_mount_target.uploads) == 0
    error_message = "기본값으로 EFS를 만들면 안 됩니다."
  }
}

run "photo_storage_with_efs" {
  command = plan

  module {
    source = "../../modules/photo-storage"
  }

  variables {
    name                  = "eum-test"
    enable_efs            = true
    app_subnet_ids        = ["subnet-a", "subnet-b"]
    efs_security_group_id = "sg-efs"
    app_posix_id          = 10001
  }

  assert {
    condition     = length(aws_efs_file_system.uploads) == 1 && length(aws_efs_mount_target.uploads) == 2
    error_message = "켜면 EFS와 AZ마다 마운트 타깃이 있어야 합니다."
  }
}

run "api_runtime_without_alb_and_service" {
  command = plan

  module {
    source = "../../modules/api-runtime"
  }

  variables {
    name                     = "eum-test"
    aws_region               = "ap-northeast-2"
    api_port                 = 8080
    vpc_id                   = "vpc-test"
    public_subnet_ids        = ["subnet-public-a", "subnet-public-b"]
    app_subnet_ids           = ["subnet-app-a", "subnet-app-b"]
    alb_security_group_id    = "sg-alb"
    app_security_group_id    = "sg-app"
    trusted_proxy_cidrs      = ["10.20.0.0/24", "10.20.1.0/24"]
    desired_count            = 1
    refresh_cookie_same_site = "Strict"
    upload_storage           = "local"
    db_name                  = "shopping"
    uploads_bucket           = "eum-test-uploads"
    uploads_cdn_url          = "https://cdn.eum-shopping.test"
  }

  assert {
    condition = (
      length(aws_lb.api) + length(aws_ecs_task_definition.api) + length(aws_ecs_service.api)
    ) == 0
    error_message = "기본값으로 ALB와 ECS 서비스를 만들면 안 됩니다."
  }
}

run "api_runtime_with_everything" {
  command = plan

  module {
    source = "../../modules/api-runtime"
  }

  variables {
    name                     = "eum-test"
    aws_region               = "ap-northeast-2"
    api_port                 = 8080
    enable_alb               = true
    enable_service           = true
    enable_efs               = true
    vpc_id                   = "vpc-test"
    public_subnet_ids        = ["subnet-public-a", "subnet-public-b"]
    app_subnet_ids           = ["subnet-app-a", "subnet-app-b"]
    alb_security_group_id    = "sg-alb"
    app_security_group_id    = "sg-app"
    trusted_proxy_cidrs      = ["10.20.0.0/24", "10.20.1.0/24"]
    desired_count            = 1
    refresh_cookie_same_site = "Strict"
    upload_storage           = "local"
    db_name                  = "shopping"
    db_address               = "db.eum-shopping.test"
    redis_endpoint           = "redis.eum-shopping.test"
    redis_secret_arns        = ["arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:redis"]
    uploads_bucket           = "eum-test-uploads"
    uploads_cdn_url          = "https://cdn.eum-shopping.test"
    efs_file_system_id       = "fs-test"
    efs_file_system_arn      = "arn:aws:elasticfilesystem:ap-northeast-2:123456789012:file-system/fs-test"
    efs_access_point_id      = "fsap-test"
    efs_access_point_arn     = "arn:aws:elasticfilesystem:ap-northeast-2:123456789012:access-point/fsap-test"
  }

  assert {
    condition = alltrue([
      length(aws_lb.api) == 1,
      length(aws_ecs_service.api) == 1,
      length(aws_ecs_task_definition.api[0].volume) == 1,
      length(aws_iam_role_policy.efs) == 1,
    ])
    error_message = "모두 켰는데 빠진 리소스가 있습니다."
  }
}

run "api_runtime_with_s3_storage_has_no_volume" {
  command = plan

  module {
    source = "../../modules/api-runtime"
  }

  variables {
    name                     = "eum-test"
    aws_region               = "ap-northeast-2"
    api_port                 = 8080
    enable_alb               = true
    enable_service           = true
    vpc_id                   = "vpc-test"
    public_subnet_ids        = ["subnet-public-a", "subnet-public-b"]
    app_subnet_ids           = ["subnet-app-a", "subnet-app-b"]
    alb_security_group_id    = "sg-alb"
    app_security_group_id    = "sg-app"
    trusted_proxy_cidrs      = ["10.20.0.0/24", "10.20.1.0/24"]
    desired_count            = 1
    refresh_cookie_same_site = "Strict"
    upload_storage           = "s3"
    db_name                  = "shopping"
    db_address               = "db.eum-shopping.test"
    redis_endpoint           = "redis.eum-shopping.test"
    redis_secret_arns        = ["arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:redis"]
    uploads_bucket           = "eum-test-uploads"
    uploads_cdn_url          = "https://cdn.eum-shopping.test"
  }

  assert {
    condition     = length(aws_ecs_task_definition.api[0].volume) == 0 && length(aws_iam_role_policy.efs) == 0
    error_message = "EFS를 끄면 태스크 정의의 볼륨과 EFS 권한이 없어야 합니다."
  }
}

run "budget_alerts_every_recipient_at_each_threshold" {
  command = plan

  module {
    source = "../../modules/observability"
  }

  variables {
    name             = "eum-test"
    limit_usd        = 200
    alert_emails     = ["ops@eum-shopping.test", "dev@eum-shopping.test"]
    alert_thresholds = [50, 80, 100]
  }

  assert {
    condition     = aws_budgets_budget.monthly.limit_amount == "200"
    error_message = "월 예산이 입력값과 같아야 합니다."
  }

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
