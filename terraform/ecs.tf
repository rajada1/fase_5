# ecs.tf - ECS Fargate Services
# Free Tier note: ECS Fargate is NOT free, but is the cheapest container option.
# With minimal resources (256 CPU / 512 MB), 6 services cost ~$50-70/month.
# Alternative: Use a single EC2 t2.micro (free) with docker-compose, but loses
# the microservices orchestration benefits required by the hackathon.

resource "aws_ecs_cluster" "main" {
  name = "architecture-cluster-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "disabled" # Disable to save costs
  }
}

# ============================================================
# IAM Roles
# ============================================================

resource "aws_iam_role" "ecs_execution_role" {
  name = "ecs-execution-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task_role" {
  name = "ecs-task-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy" "ecs_task_policy" {
  name = "ecs-task-policy-${var.environment}"
  role = aws_iam_role.ecs_task_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "S3Access"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.diagrams.arn,
          "${aws_s3_bucket.diagrams.arn}/*"
        ]
      },
      {
        Sid    = "SQSAccess"
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:ChangeMessageVisibility",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl"
        ]
        Resource = [
          aws_sqs_queue.file_uploaded_queue.arn,
          aws_sqs_queue.diagram_processed_queue.arn,
          aws_sqs_queue.analysis_completed_queue.arn,
          aws_sqs_queue.status_file_uploaded_queue.arn,
          aws_sqs_queue.status_diagram_processed_queue.arn,
          aws_sqs_queue.status_analysis_completed_queue.arn,
        ]
      },
      {
        Sid    = "SNSPublish"
        Effect = "Allow"
        Action = ["sns:Publish"]
        Resource = [
          aws_sns_topic.file_uploaded.arn,
          aws_sns_topic.diagram_processed.arn,
          aws_sns_topic.analysis_completed.arn,
        ]
      },
      {
        Sid      = "TextractAccess"
        Effect   = "Allow"
        Action   = ["textract:DetectDocumentText"]
        Resource = "*"
      }
    ]
  })
}

# ============================================================
# CloudWatch Log Groups (5GB/month free)
# ============================================================
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/ecs/api-gateway-${var.environment}"
  retention_in_days = 7 # Short retention to save storage
}

resource "aws_cloudwatch_log_group" "upload_service" {
  name              = "/ecs/upload-service-${var.environment}"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "processing_service" {
  name              = "/ecs/processing-service-${var.environment}"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "ai_analysis_service" {
  name              = "/ecs/ai-analysis-service-${var.environment}"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "report_service" {
  name              = "/ecs/report-service-${var.environment}"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "status_service" {
  name              = "/ecs/status-service-${var.environment}"
  retention_in_days = 7
}

# ============================================================
# ECS Task Definitions (minimal resources for cost savings)
# ============================================================

# --- API Gateway ---
resource "aws_ecs_task_definition" "api_gateway" {
  family                   = "api-gateway-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "api-gateway"
    image     = "${aws_ecr_repository.api_gateway.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "JAVA_OPTS", value = "-Xmx384m -Xms256m" },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "COGNITO_USER_POOL_ID", value = aws_cognito_user_pool.pool.id },
      { name = "ALLOW_INSECURE_LOCAL_API", value = "false" },
      { name = "UPLOAD_SERVICE_URL", value = "http://upload-service.architecture.local:8081" },
      { name = "REPORT_SERVICE_URL", value = "http://report-service.architecture.local:8084" },
      { name = "STATUS_SERVICE_URL", value = "http://status-service.architecture.local:8085" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api_gateway.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# --- Upload Service ---
resource "aws_ecs_task_definition" "upload_service" {
  family                   = "upload-service-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "upload-service"
    image     = "${aws_ecr_repository.upload_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8081
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "JAVA_OPTS", value = "-Xmx384m -Xms256m" },
      { name = "DB_HOST", value = aws_db_instance.postgres.address },
      { name = "DB_NAME", value = "upload_db" },
      { name = "DB_USER", value = var.db_username },
      { name = "DB_PASS", value = var.db_password },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT", value = "" },
      { name = "AWS_S3_BUCKET_NAME", value = aws_s3_bucket.diagrams.bucket },
      { name = "AWS_SNS_FILE_UPLOADED_TOPIC_ARN", value = aws_sns_topic.file_uploaded.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.upload_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# --- Processing Service ---
resource "aws_ecs_task_definition" "processing_service" {
  family                   = "processing-service-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "processing-service"
    image     = "${aws_ecr_repository.processing_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8082
      protocol      = "tcp"
    }]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT_URL", value = "" },
      { name = "REDIS_URL", value = "" },
      { name = "S3_BUCKET_NAME", value = aws_s3_bucket.diagrams.bucket },
      { name = "SQS_QUEUE_URL", value = aws_sqs_queue.file_uploaded_queue.id },
      { name = "SNS_TOPIC_ARN", value = aws_sns_topic.diagram_processed.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.processing_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# --- AI Analysis Service ---
resource "aws_ecs_task_definition" "ai_analysis_service" {
  family                   = "ai-analysis-service-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "ai-analysis-service"
    image     = "${aws_ecr_repository.ai_analysis_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8083
      protocol      = "tcp"
    }]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT_URL", value = "" },
      { name = "REDIS_URL", value = "" },
      { name = "SQS_QUEUE_URL", value = aws_sqs_queue.diagram_processed_queue.id },
      { name = "SNS_TOPIC_ARN", value = aws_sns_topic.analysis_completed.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ai_analysis_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# --- Report Service ---
resource "aws_ecs_task_definition" "report_service" {
  family                   = "report-service-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "report-service"
    image     = "${aws_ecr_repository.report_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8084
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "JAVA_OPTS", value = "-Xmx384m -Xms256m" },
      { name = "DB_HOST", value = aws_db_instance.postgres.address },
      { name = "DB_NAME", value = "report_db" },
      { name = "DB_USER", value = var.db_username },
      { name = "DB_PASS", value = var.db_password },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT", value = "" },
      { name = "AWS_SQS_ANALYSIS_COMPLETED_QUEUE_URL", value = aws_sqs_queue.analysis_completed_queue.id }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.report_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# --- Status Service ---
resource "aws_ecs_task_definition" "status_service" {
  family                   = "status-service-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "status-service"
    image     = "${aws_ecr_repository.status_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8085
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "JAVA_OPTS", value = "-Xmx384m -Xms256m" },
      { name = "DB_HOST", value = aws_db_instance.postgres.address },
      { name = "DB_NAME", value = "status_db" },
      { name = "DB_USER", value = var.db_username },
      { name = "DB_PASS", value = var.db_password },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT", value = "" },
      { name = "AWS_SQS_FILE_UPLOADED_QUEUE_URL", value = aws_sqs_queue.status_file_uploaded_queue.id },
      { name = "AWS_SQS_DIAGRAM_PROCESSED_QUEUE_URL", value = aws_sqs_queue.status_diagram_processed_queue.id },
      { name = "AWS_SQS_ANALYSIS_COMPLETED_QUEUE_URL", value = aws_sqs_queue.status_analysis_completed_queue.id }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.status_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

# ============================================================
# ECS Services
# ============================================================

resource "aws_ecs_service" "api_gateway" {
  name            = "api-gateway-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api_gateway.arn
  desired_count   = 1 # Single instance for free tier
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true # Required without NAT Gateway
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api_gateway.arn
    container_name   = "api-gateway"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_ecs_service" "upload_service" {
  name            = "upload-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.upload_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.upload_service.arn
  }
}

resource "aws_ecs_service" "processing_service" {
  name            = "processing-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.processing_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }
}

resource "aws_ecs_service" "ai_analysis_service" {
  name            = "ai-analysis-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ai_analysis_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }
}

resource "aws_ecs_service" "report_service" {
  name            = "report-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.report_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.report_service.arn
  }
}

resource "aws_ecs_service" "status_service" {
  name            = "status-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.status_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_1.id, aws_subnet.public_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.status_service.arn
  }
}
