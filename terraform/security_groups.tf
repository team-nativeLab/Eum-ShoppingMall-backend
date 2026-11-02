# 보안 그룹. 들어오는 트래픽은 인터넷 → ALB → 앱 → RDS·Redis·EFS 순서로만 허용한다.
# description은 주석이 아니라 AWS에 저장되는 값이다. 바꾸면 보안 그룹이 교체되고 한글도 쓸 수 없어 영어로 둔다.

resource "aws_security_group" "alb" {
  name_prefix = "${var.name}-alb-"
  description = "Public HTTPS entry point"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTP redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = local.api_port
    to_port     = local.api_port
    protocol    = "tcp"
    cidr_blocks = var.app_subnet_cidrs
  }
}

# 앱은 결제·메일 같은 외부 API를 호출하므로 나가는 트래픽을 막지 않는다.
resource "aws_security_group" "app" {
  name_prefix = "${var.name}-app-"
  description = "Private ECS tasks; only ALB can reach the application"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = local.api_port
    to_port         = local.api_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${var.name}-db-"
  description = "MySQL reachable only from ECS tasks"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

resource "aws_security_group" "redis" {
  name_prefix = "${var.name}-redis-"
  description = "Redis TLS reachable only from ECS tasks"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

resource "aws_security_group" "efs" {
  name_prefix = "${var.name}-efs-"
  description = "EFS NFS reachable only from ECS tasks"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}
