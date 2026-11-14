# VPC와 서브넷. AZ 2개에 서브넷을 종류별로 하나씩 둔다.
# - 공용: ALB, NAT Gateway. 인터넷 게이트웨이로 바로 나간다.
# - 앱: ECS 태스크, EFS 마운트 타깃. NAT Gateway를 거쳐서만 나간다.
# - 데이터: RDS, Redis. 인터넷으로 나가는 경로가 없다.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # 서브넷을 나눠 둘 AZ 2개
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true
}

resource "aws_subnet" "app" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.app_subnet_cidrs[count.index]
  availability_zone = local.azs[count.index]
}

resource "aws_subnet" "data" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.data_subnet_cidrs[count.index]
  availability_zone = local.azs[count.index]
}

# 공용 서브넷 라우팅
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# NAT Gateway는 enable_nat_gateway를 켜야 만든다.
# 첫 번째 공용 서브넷에 1개만 둔다. 그 AZ에 장애가 나면 앱 서브넷의 외부 통신이 끊긴다.
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? 1 : 0
  domain = "vpc"
}

resource "aws_nat_gateway" "main" {
  count         = var.enable_nat_gateway ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id
  depends_on    = [aws_internet_gateway.main]
}

# 앱 서브넷 라우팅. NAT Gateway를 끄면 함께 없어져 앱 서브넷은 VPC 안에서만 통신한다.
resource "aws_route_table" "app" {
  count  = var.enable_nat_gateway ? 1 : 0
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[0].id
  }
}

resource "aws_route_table_association" "app" {
  count          = var.enable_nat_gateway ? 2 : 0
  subnet_id      = aws_subnet.app[count.index].id
  route_table_id = aws_route_table.app[0].id
}

# 데이터 서브넷 라우팅. 인터넷으로 나가는 기본 경로를 두지 않는다.
resource "aws_route_table" "data" {
  vpc_id = aws_vpc.main.id
}

resource "aws_route_table_association" "data" {
  count          = 2
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.data.id
}
