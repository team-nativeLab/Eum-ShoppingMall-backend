# ElastiCache Redis. enable_data_layer를 켜야 만든다. 전송·저장 암호화와 AUTH 토큰을 켠다.
# ECS가 Secrets Manager에서 토큰을 읽어 앱에 넘긴다. 토큰 값은 Terraform 상태에도 저장되므로 상태 버킷을 암호화해야 한다.

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name}-redis"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_secretsmanager_secret" "redis" {
  count                   = var.enable_data_layer ? 1 : 0
  name                    = "${var.name}/redis-auth"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis" {
  count         = var.enable_data_layer ? 1 : 0
  secret_id     = aws_secretsmanager_secret.redis[0].id
  secret_string = var.redis_auth_token
}

resource "aws_elasticache_replication_group" "main" {
  count                      = var.enable_data_layer ? 1 : 0
  replication_group_id       = "${var.name}-redis"
  description                = "Shopping session and request-limit Redis"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  parameter_group_name       = "default.redis7"
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = var.redis_auth_token
  auth_token_update_strategy = "ROTATE"
  automatic_failover_enabled = false
  multi_az_enabled           = false
  snapshot_retention_limit   = 1
  apply_immediately          = false
}
