# 쇼핑몰 API 한 벌. 모듈은 리소스를 만들기만 하고, 무엇을 켜고 어떻게 잇는지는 여기서 정한다.

module "network" {
  source = "../../modules/network"

  name                = var.name
  vpc_cidr            = var.vpc_cidr
  public_subnet_cidrs = var.public_subnet_cidrs
  app_subnet_cidrs    = var.app_subnet_cidrs
  data_subnet_cidrs   = var.data_subnet_cidrs
  enable_nat_gateway  = var.enable_nat_gateway
  api_port            = local.api_port
}

module "database" {
  source = "../../modules/database"

  name                       = var.name
  enabled                    = var.enable_data_layer
  subnet_ids                 = module.network.data_subnet_ids
  database_security_group_id = module.network.database_security_group_id
  redis_security_group_id    = module.network.redis_security_group_id
  db_name                    = var.db_name
  db_username                = var.db_username
  db_instance_class          = var.db_instance_class
  db_multi_az                = var.db_multi_az
  redis_node_type            = var.redis_node_type
  redis_auth_token           = var.redis_auth_token
}

module "photo_storage" {
  source = "../../modules/photo-storage"

  name                  = var.name
  cors_allowed_origins  = var.cors_allowed_origins
  enable_efs            = var.enable_efs
  app_subnet_ids        = module.network.app_subnet_ids
  efs_security_group_id = module.network.efs_security_group_id
  app_posix_id          = local.app_posix_id
}

module "api_runtime" {
  source = "../../modules/api-runtime"

  name           = var.name
  aws_region     = var.aws_region
  api_port       = local.api_port
  enable_alb     = var.enable_alb
  enable_service = var.enable_ecs_service
  enable_efs     = var.enable_efs

  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  app_subnet_ids        = module.network.app_subnet_ids
  alb_security_group_id = module.network.alb_security_group_id
  app_security_group_id = module.network.app_security_group_id
  # ALB가 있는 공용 서브넷만 프록시로 믿는다.
  trusted_proxy_cidrs = var.public_subnet_cidrs

  certificate_arn         = var.certificate_arn
  image_tag               = var.image_tag
  desired_count           = var.desired_count
  app_environment         = var.app_environment
  application_secret_arns = var.application_secret_arns
  database_app_secret_arn = var.database_app_secret_arn
  # 마이그레이션 전용 계정은 migrate 작업만 받는다(#131). 마스터 시크릿은 잘못 넣지 않았는지 확인하는 데만 쓴다.
  database_migration_secret_arn = var.database_migration_secret_arn
  database_master_secret_arn    = module.database.db_master_secret_arn
  cors_allowed_origins          = var.cors_allowed_origins
  refresh_cookie_same_site      = var.refresh_cookie_same_site
  upload_storage                = var.upload_storage

  db_name              = var.db_name
  db_address           = module.database.db_address
  redis_endpoint       = module.database.redis_endpoint
  redis_secret_arns    = module.database.redis_secret_arns
  uploads_bucket       = module.photo_storage.bucket
  uploads_cdn_url      = module.photo_storage.cdn_url
  efs_file_system_id   = module.photo_storage.efs_file_system_id
  efs_file_system_arn  = module.photo_storage.efs_file_system_arn
  efs_access_point_id  = module.photo_storage.efs_access_point_id
  efs_access_point_arn = module.photo_storage.efs_access_point_arn
}

module "observability" {
  source = "../../modules/observability"

  name             = var.name
  limit_usd        = var.budget_limit_usd
  alert_emails     = var.budget_alert_emails
  alert_thresholds = var.budget_alert_thresholds

  # RDS·Redis 이상 징후 경보(#145). 예산 알림과 같은 담당자에게 보낸다.
  data_layer_enabled      = var.enable_data_layer
  db_instance_identifier  = module.database.db_instance_identifier
  db_master_username      = var.db_username
  db_audit_log_group_name = module.database.db_audit_log_group_name
  redis_cluster_id        = module.database.redis_cluster_id
}
