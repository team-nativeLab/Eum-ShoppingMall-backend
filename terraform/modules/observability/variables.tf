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
