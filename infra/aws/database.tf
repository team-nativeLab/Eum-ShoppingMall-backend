# RDS MySQL. 마스터 비밀번호는 AWS가 Secrets Manager에서 관리한다(rds_master_secret_arn 출력).
# 앱은 마스터 계정을 쓰지 않고 database_app_secret_arn의 최소 권한 계정으로 접속한다.

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_db_instance" "main" {
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
  vpc_security_group_ids          = [aws_security_group.database.id]
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
