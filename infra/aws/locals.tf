locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
  app_secrets = [
    for name, arn in var.application_secret_arns : {
      name      = name
      valueFrom = arn
    }
  ]
}
