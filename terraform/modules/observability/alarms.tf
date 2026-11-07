# RDS·Redis 이상 징후 경보(#145). 데이터 계층을 켤 때만 만든다. 경보 하나는 월 $0.10이다.
# 예산 알림과 같은 담당자 이메일로 SNS를 거쳐 보낸다. 받는 사람이 처음 받는 "AWS Notification - Subscription Confirmation"
# 메일에서 구독을 확인해야 알림이 간다.

locals {
  alarms_enabled = var.data_layer_enabled
  alarm_topic    = one(aws_sns_topic.alarms[*].arn)
}

resource "aws_sns_topic" "alarms" {
  count = local.alarms_enabled ? 1 : 0
  name  = "${var.name}-alarms"
}

resource "aws_sns_topic_subscription" "alarm_email" {
  for_each  = local.alarms_enabled ? toset(var.alert_emails) : toset([])
  topic_arn = aws_sns_topic.alarms[0].arn
  protocol  = "email"
  endpoint  = each.value
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-rds-cpu-high"
  alarm_description   = "RDS CPU가 15분 동안 80% 이상입니다. 느린 쿼리 로그와 관리자 대량 조회 알림을 확인합니다."
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = var.db_instance_identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [local.alarm_topic]
  ok_actions          = [local.alarm_topic]
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-rds-storage-low"
  alarm_description   = "RDS 남은 저장 공간이 2GiB 이하입니다. 저장 공간 자동 확장(최대 100GB)과 감사·정리 작업을 확인합니다."
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = var.db_instance_identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2147483648
  comparison_operator = "LessThanOrEqualToThreshold"
  alarm_actions       = [local.alarm_topic]
  ok_actions          = [local.alarm_topic]
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-rds-connections-high"
  alarm_description   = "RDS 연결 수가 10분 동안 기준 이상입니다. 앱 커넥션 풀 크기·작업 수와 DB에 직접 붙은 접속(감사 로그)을 확인합니다."
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = var.db_instance_identifier }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.db_connection_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [local.alarm_topic]
  ok_actions          = [local.alarm_topic]
}

resource "aws_cloudwatch_metric_alarm" "redis_memory" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-redis-memory-high"
  alarm_description   = "Redis 메모리 사용률이 10분 동안 80% 이상입니다. 키가 계속 쌓이는지(요청 제한·잠금·캐시) 확인합니다."
  namespace           = "AWS/ElastiCache"
  metric_name         = "DatabaseMemoryUsagePercentage"
  dimensions          = { CacheClusterId = var.redis_cluster_id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [local.alarm_topic]
  ok_actions          = [local.alarm_topic]
}

resource "aws_cloudwatch_metric_alarm" "redis_cpu" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-redis-cpu-high"
  alarm_description   = "Redis 엔진 CPU가 15분 동안 80% 이상입니다."
  namespace           = "AWS/ElastiCache"
  metric_name         = "EngineCPUUtilization"
  dimensions          = { CacheClusterId = var.redis_cluster_id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [local.alarm_topic]
  ok_actions          = [local.alarm_topic]
}

# RDS 마스터 계정을 쓰면 알린다(#132). 마스터 계정은 DB 계정을 만들 때만 쓰므로, 그 밖의 사용은 감사 로그를 확인한다.
# 감사 로그 한 줄은 "시각,서버,계정,접속 주소,연결 ID,쿼리 ID,작업,DB,대상,결과" 형식이라 ",계정," 문자열로 찾는다.
resource "aws_cloudwatch_log_metric_filter" "db_master_user" {
  count          = local.alarms_enabled ? 1 : 0
  name           = "${var.name}-db-master-user"
  log_group_name = var.db_audit_log_group_name
  pattern        = "\",${var.db_master_username},\""

  metric_transformation {
    name      = "MasterUserActivity"
    namespace = "${var.name}/Database"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "db_master_user" {
  count               = local.alarms_enabled ? 1 : 0
  alarm_name          = "${var.name}-db-master-user"
  alarm_description   = "RDS 마스터 계정으로 접속하거나 쿼리를 실행했습니다. 계정 만들기 같은 예정된 작업이 아니면 RDS 감사 로그를 확인합니다."
  namespace           = "${var.name}/Database"
  metric_name         = "MasterUserActivity"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [local.alarm_topic]

  depends_on = [aws_cloudwatch_log_metric_filter.db_master_user]
}
