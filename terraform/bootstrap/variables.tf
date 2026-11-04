variable "name" {
  description = "버킷 이름 앞에 붙는 접두어. stacks/app의 name과 같게 둔다."
  type        = string
  default     = "eum-shopping"
}

variable "aws_region" {
  description = "버킷을 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}
