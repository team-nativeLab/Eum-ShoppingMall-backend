variable "name" {
  description = "리소스 이름 앞에 붙는 접두어"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
}

variable "public_subnet_cidrs" {
  description = "공용 서브넷 CIDR(ALB, NAT Gateway). AZ마다 하나씩 2개."
  type        = list(string)
}

variable "app_subnet_cidrs" {
  description = "앱 서브넷 CIDR(ECS 태스크, EFS). AZ마다 하나씩 2개."
  type        = list(string)
}

variable "data_subnet_cidrs" {
  description = "데이터 서브넷 CIDR(RDS, Redis). AZ마다 하나씩 2개."
  type        = list(string)
}

variable "enable_nat_gateway" {
  description = "NAT Gateway를 만든다. 쓰지 않아도 요금이 나온다."
  type        = bool
  default     = false
}

variable "api_port" {
  description = "앱이 요청을 받는 포트. ALB와 앱 보안 그룹이 이 포트만 연다."
  type        = number
}
