# 모듈로 나누기 전(terraform/ 바로 아래)에 만든 리소스의 state 주소를 모듈 안으로 옮긴다.
# 이미 apply한 환경에서 리소스를 지우고 다시 만들지 않게 하려는 것이다. 모든 환경에 한 번씩 apply한 뒤에는 지워도 된다.

moved {
  from = aws_vpc.main
  to   = module.network.aws_vpc.main
}

moved {
  from = aws_internet_gateway.main
  to   = module.network.aws_internet_gateway.main
}

moved {
  from = aws_subnet.public
  to   = module.network.aws_subnet.public
}

moved {
  from = aws_subnet.app
  to   = module.network.aws_subnet.app
}

moved {
  from = aws_subnet.data
  to   = module.network.aws_subnet.data
}

moved {
  from = aws_route_table.public
  to   = module.network.aws_route_table.public
}

moved {
  from = aws_route_table_association.public
  to   = module.network.aws_route_table_association.public
}

moved {
  from = aws_eip.nat
  to   = module.network.aws_eip.nat
}

moved {
  from = aws_nat_gateway.main
  to   = module.network.aws_nat_gateway.main
}

moved {
  from = aws_route_table.app
  to   = module.network.aws_route_table.app
}

moved {
  from = aws_route_table_association.app
  to   = module.network.aws_route_table_association.app
}

moved {
  from = aws_route_table.data
  to   = module.network.aws_route_table.data
}

moved {
  from = aws_route_table_association.data
  to   = module.network.aws_route_table_association.data
}

moved {
  from = aws_security_group.alb
  to   = module.network.aws_security_group.alb
}

moved {
  from = aws_security_group.app
  to   = module.network.aws_security_group.app
}

moved {
  from = aws_security_group.database
  to   = module.network.aws_security_group.database
}

moved {
  from = aws_security_group.redis
  to   = module.network.aws_security_group.redis
}

moved {
  from = aws_security_group.efs
  to   = module.network.aws_security_group.efs
}

moved {
  from = aws_db_subnet_group.main
  to   = module.database.aws_db_subnet_group.main
}

moved {
  from = aws_db_instance.main
  to   = module.database.aws_db_instance.main
}

moved {
  from = aws_elasticache_subnet_group.main
  to   = module.database.aws_elasticache_subnet_group.main
}

moved {
  from = aws_secretsmanager_secret.redis
  to   = module.database.aws_secretsmanager_secret.redis
}

moved {
  from = aws_secretsmanager_secret_version.redis
  to   = module.database.aws_secretsmanager_secret_version.redis
}

moved {
  from = aws_elasticache_replication_group.main
  to   = module.database.aws_elasticache_replication_group.main
}

moved {
  from = aws_efs_file_system.uploads
  to   = module.photo_storage.aws_efs_file_system.uploads
}

moved {
  from = aws_efs_access_point.uploads
  to   = module.photo_storage.aws_efs_access_point.uploads
}

moved {
  from = aws_efs_mount_target.uploads
  to   = module.photo_storage.aws_efs_mount_target.uploads
}

moved {
  from = aws_s3_bucket.uploads
  to   = module.photo_storage.aws_s3_bucket.uploads
}

moved {
  from = aws_s3_bucket_public_access_block.uploads
  to   = module.photo_storage.aws_s3_bucket_public_access_block.uploads
}

moved {
  from = aws_s3_bucket_ownership_controls.uploads
  to   = module.photo_storage.aws_s3_bucket_ownership_controls.uploads
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.uploads
  to   = module.photo_storage.aws_s3_bucket_server_side_encryption_configuration.uploads
}

moved {
  from = aws_s3_bucket_versioning.uploads
  to   = module.photo_storage.aws_s3_bucket_versioning.uploads
}

moved {
  from = aws_s3_bucket_lifecycle_configuration.uploads
  to   = module.photo_storage.aws_s3_bucket_lifecycle_configuration.uploads
}

moved {
  from = aws_s3_bucket_cors_configuration.uploads
  to   = module.photo_storage.aws_s3_bucket_cors_configuration.uploads
}

moved {
  from = aws_cloudfront_origin_access_control.uploads
  to   = module.photo_storage.aws_cloudfront_origin_access_control.uploads
}

moved {
  from = aws_cloudfront_distribution.uploads
  to   = module.photo_storage.aws_cloudfront_distribution.uploads
}

moved {
  from = aws_s3_bucket_policy.uploads
  to   = module.photo_storage.aws_s3_bucket_policy.uploads
}

moved {
  from = aws_iam_user.uploads
  to   = module.photo_storage.aws_iam_user.uploads
}

moved {
  from = aws_iam_user_policy.uploads
  to   = module.photo_storage.aws_iam_user_policy.uploads
}

moved {
  from = aws_lb.api
  to   = module.api_runtime.aws_lb.api
}

moved {
  from = aws_lb_target_group.api
  to   = module.api_runtime.aws_lb_target_group.api
}

moved {
  from = aws_lb_listener.http
  to   = module.api_runtime.aws_lb_listener.http
}

moved {
  from = aws_lb_listener.https
  to   = module.api_runtime.aws_lb_listener.https
}

moved {
  from = aws_ecr_repository.api
  to   = module.api_runtime.aws_ecr_repository.api
}

moved {
  from = aws_ecr_lifecycle_policy.api
  to   = module.api_runtime.aws_ecr_lifecycle_policy.api
}

moved {
  from = aws_cloudwatch_log_group.api
  to   = module.api_runtime.aws_cloudwatch_log_group.api
}

moved {
  from = aws_ecs_cluster.main
  to   = module.api_runtime.aws_ecs_cluster.main
}

moved {
  from = aws_ecs_task_definition.api
  to   = module.api_runtime.aws_ecs_task_definition.api
}

moved {
  from = aws_ecs_service.api
  to   = module.api_runtime.aws_ecs_service.api
}

moved {
  from = aws_iam_role.execution
  to   = module.api_runtime.aws_iam_role.execution
}

moved {
  from = aws_iam_role_policy_attachment.execution
  to   = module.api_runtime.aws_iam_role_policy_attachment.execution
}

moved {
  from = aws_iam_role_policy.secrets
  to   = module.api_runtime.aws_iam_role_policy.secrets
}

moved {
  from = aws_iam_role.task
  to   = module.api_runtime.aws_iam_role.task
}

moved {
  from = aws_iam_role_policy.efs
  to   = module.api_runtime.aws_iam_role_policy.efs
}

moved {
  from = aws_budgets_budget.monthly
  to   = module.observability.aws_budgets_budget.monthly
}
