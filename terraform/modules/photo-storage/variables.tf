variable "name" {
  description = "리소스 이름 앞에 붙는 접두어"
  type        = string
}

variable "cors_allowed_origins" {
  description = "브라우저가 버킷에 직접 PUT 업로드할 수 있는 origin. 쉼표로 구분한다."
  type        = string
}

variable "enable_efs" {
  description = "upload_storage = \"local\"일 때 쓰는 EFS를 만든다."
  type        = bool
  default     = false
}

variable "app_subnet_ids" {
  description = "EFS 마운트 타깃을 둘 앱 서브넷"
  type        = list(string)
}

variable "efs_security_group_id" {
  type = string
}

variable "app_posix_id" {
  description = "Dockerfile의 USER와 같은 UID·GID. EFS 액세스 포인트가 이 사용자로 파일을 읽고 쓴다."
  type        = number
}
