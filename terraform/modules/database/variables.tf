variable "name" {
  description = "리소스 이름 앞에 붙는 접두어"
  type        = string
}

variable "enabled" {
  description = "RDS와 Redis를 만든다. 쓰지 않아도 요금이 나온다."
  type        = bool
  default     = false
}

variable "subnet_ids" {
  description = "RDS와 Redis를 둘 데이터 서브넷"
  type        = list(string)
}

variable "database_security_group_id" {
  type = string
}

variable "redis_security_group_id" {
  type = string
}

variable "db_name" {
  type = string
}

variable "db_username" {
  type = string
}

variable "db_instance_class" {
  type = string
}

variable "db_multi_az" {
  type = bool
}

variable "redis_node_type" {
  type = string
}

variable "redis_auth_token" {
  type      = string
  sensitive = true
}

variable "audit_excluded_users" {
  description = "DB 감사 로그에서 쿼리를 남기지 않을 계정. 앱 계정과 RDS가 상태 확인에 쓰는 rdsadmin. 접속 기록은 이 계정들도 남는다."
  type        = list(string)
  default     = ["rdsadmin", "shopping_app"]
}
