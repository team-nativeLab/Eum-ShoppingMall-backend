# RDS MySQL. enabled(스택의 enable_data_layer)를 켜야 만든다.
# 마스터 비밀번호는 AWS가 Secrets Manager에서 관리한다(rds_master_secret_arn 출력).
# 앱은 마스터 계정을 쓰지 않고 database_app_secret_arn의 최소 권한 계정으로 접속한다.

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = var.subnet_ids
}

# 기본 파라미터 그룹은 TLS가 아닌 평문 접속도 받는다(require_secure_transport=0). TLS 접속만 받게 한다(#133).
# require_secure_transport는 동적 파라미터라 재부팅 없이 바로 적용된다.
resource "aws_db_parameter_group" "main" {
  count       = var.enabled ? 1 : 0
  name        = "${var.name}-mysql84"
  family      = "mysql8.4"
  description = "${var.name} MySQL 8.4 parameters"

  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
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
  vpc_security_group_ids          = [var.database_security_group_id]
  publicly_accessible             = false
  multi_az                        = var.db_multi_az
  backup_retention_period         = 7
  auto_minor_version_upgrade      = true
  deletion_protection             = true
  copy_tags_to_snapshot           = true
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${var.name}-mysql-final"
  enabled_cloudwatch_logs_exports = ["error", "slowquery"]
}
