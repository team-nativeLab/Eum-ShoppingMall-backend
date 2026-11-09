# RDS MySQL. enabled(스택의 enable_data_layer)를 켜야 만든다.
# 마스터 비밀번호는 AWS가 Secrets Manager에서 관리한다(rds_master_secret_arn 출력).
# 앱은 마스터 계정을 쓰지 않고 database_app_secret_arn의 최소 권한 계정으로 접속한다.

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = var.subnet_ids
}

locals {
  # RDS가 CloudWatch Logs로 내보내는 로그와 보관 기간(일, #132).
  # RDS가 로그 그룹을 직접 만들면 보관 기간이 "만료 없음"이 되므로 미리 만든다.
  # 감사 로그는 관리자 감사 로그와 같은 접속기록 보관 기준(2년)에 맞춘다. CloudWatch는 731일만 받는다.
  # 느린 쿼리 로그에는 SQL에 넣은 값(이메일·연락처 등)이 함께 남으므로 앱 로그와 같이 짧게 둔다.
  db_log_retention_days = {
    audit     = 731
    error     = 30
    slowquery = 30
  }
}

# 기본 파라미터 그룹은 TLS가 아닌 평문 접속도 받는다(require_secure_transport=0). TLS 접속만 받게 한다(#133).
# 기본값이 slow_query_log=0이라 느린 쿼리 로그를 내보내도 비어 있으므로 켠다(#132).
# 모두 동적 파라미터라 재부팅 없이 바로 적용된다.
resource "aws_db_parameter_group" "main" {
  count       = var.enabled ? 1 : 0
  name        = "${var.name}-mysql84"
  family      = "mysql8.4"
  description = "${var.name} MySQL 8.4 parameters"

  parameter {
    name  = "require_secure_transport"
    value = "1"
  }

  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  # 이 시간(초)보다 오래 걸린 쿼리를 남긴다.
  parameter {
    name  = "long_query_time"
    value = "1"
  }

  # CloudWatch Logs로 내보내려면 파일로 남겨야 한다.
  parameter {
    name  = "log_output"
    value = "FILE"
  }
}

# DB에 직접 접속해 한 일을 남긴다(#132, MariaDB 감사 플러그인).
# 접속(CONNECT)은 모든 계정을 남기고, 쿼리(QUERY)는 앱 계정과 RDS 관리 계정(rdsadmin)을 뺀 계정만 남긴다.
# 앱의 일반 쿼리까지 남기면 양과 비용이 크게 늘고, 앱의 변경은 관리자 감사 로그에 이미 남기 때문이다.
# 그래서 마스터·마이그레이션 계정이나 사람이 쓴 계정의 조회·변경·DDL·권한 변경은 모두 남는다.
resource "aws_db_option_group" "main" {
  count                    = var.enabled ? 1 : 0
  name                     = "${var.name}-mysql84"
  engine_name              = "mysql"
  major_engine_version     = "8.4"
  option_group_description = "${var.name} MySQL 8.4 audit log"

  option {
    option_name = "MARIADB_AUDIT_PLUGIN"

    option_settings {
      name  = "SERVER_AUDIT_EVENTS"
      value = "CONNECT,QUERY"
    }

    option_settings {
      name  = "SERVER_AUDIT_EXCL_USERS"
      value = join(",", var.audit_excluded_users)
    }
  }
}

resource "aws_cloudwatch_log_group" "db" {
  for_each          = var.enabled ? local.db_log_retention_days : {}
  name              = "/aws/rds/instance/${var.name}-mysql/${each.key}"
  retention_in_days = each.value
}

resource "aws_db_instance" "main" {
  count                           = var.enabled ? 1 : 0
  identifier                      = "${var.name}-mysql"
  engine                          = "mysql"
  engine_version                  = "8.4.11"
  instance_class                  = var.db_instance_class
  allocated_storage               = 20
  max_allocated_storage           = 100
  storage_type                    = "gp3"
  storage_encrypted               = true
  db_name                         = var.db_name
  username                        = var.db_username
  manage_master_user_password     = true
  db_subnet_group_name            = aws_db_subnet_group.main.name
  parameter_group_name            = aws_db_parameter_group.main[0].name
  option_group_name               = aws_db_option_group.main[0].name
  vpc_security_group_ids          = [var.database_security_group_id]
  publicly_accessible             = false
  multi_az                        = var.db_multi_az
  backup_retention_period         = 7
  auto_minor_version_upgrade      = true
  deletion_protection             = true
  copy_tags_to_snapshot           = true
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${var.name}-mysql-final"
  enabled_cloudwatch_logs_exports = keys(local.db_log_retention_days)

  # 보관 기간을 정한 로그 그룹이 먼저 있어야 RDS가 기한 없는 그룹을 따로 만들지 않는다.
  depends_on = [aws_cloudwatch_log_group.db]
}
