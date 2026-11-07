variable "name" {
  description = "리소스 이름 앞에 붙는 접두어"
  type        = string
}

variable "limit_usd" {
  description = "월 예산(USD)"
  type        = number
}

variable "alert_emails" {
  description = "예산 알림을 받을 이메일"
  type        = list(string)
}

variable "alert_thresholds" {
  description = "실제 비용이 월 예산의 몇 %를 넘으면 알릴지"
  type        = list(number)
}

variable "data_layer_enabled" {
  description = "RDS·Redis 경보를 만든다(#145). 스택의 enable_data_layer와 같다."
  type        = bool
  default     = false
}

variable "db_instance_identifier" {
  description = "경보를 걸 RDS 인스턴스 이름"
  type        = string
  default     = null
}

variable "db_master_username" {
  description = "RDS 마스터 계정 이름. 감사 로그에서 이 계정을 쓰면 알린다."
  type        = string
  default     = null
}

variable "db_audit_log_group_name" {
  description = "RDS 감사 로그가 쌓이는 CloudWatch 로그 그룹"
  type        = string
  default     = null
}

variable "redis_cluster_id" {
  description = "경보를 걸 Redis 노드 이름"
  type        = string
  default     = null
}

variable "db_connection_alarm_threshold" {
  description = "RDS 연결 수 경보 기준. db.t4g.micro는 최대 연결 수가 약 60~80개이고, 앱은 작업마다 커넥션 풀 10개를 쓴다."
  type        = number
  default     = 50
}
