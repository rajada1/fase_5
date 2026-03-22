# ecs.tf

resource "aws_ecs_cluster" "main" {
  name = "architecture-cluster-${var.environment}"
}

# IAM Role for ECS Task Execution
resource "aws_iam_role" "ecs_execution_role" {
  name = "ecs_execution_role_${var.environment}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_role_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM Role for ECS Task Operations (S3, Textract, SQS, SNS)
resource "aws_iam_role" "ecs_task_role" {
  name = "ecs_task_role_${var.environment}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "ecs_task_policy" {
  name = "ecs_task_policy_${var.environment}"
  role = aws_iam_role.ecs_task_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = [
          "${aws_s3_bucket.diagrams.arn}/*"
        ]
      },
      {
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
          aws_sqs_queue.analysis_completed_queue.arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = [
          aws_sns_topic.file_uploaded.arn,
          aws_sns_topic.diagram_processed.arn,
          aws_sns_topic.analysis_completed.arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "textract:DetectDocumentText"
        ]
        Resource = "*"
      }
    ]
  })
}

# Boilerplate example for the API Gateway Service (to be replicated for other services)
resource "aws_ecs_task_definition" "api_gateway" {
  family                   = "api-gateway-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "api-gateway"
    image     = "${aws_ecr_repository.api_gateway.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "COGNITO_USER_POOL_ID", value = var.cognito_user_pool_id }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/api-gateway-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "api_gateway_log" {
  name              = "/ecs/api-gateway-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "api_gateway_service" {
  name            = "api-gateway-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api_gateway.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api_gateway.arn
    container_name   = "api-gateway"
    container_port   = 8080
  }
}

resource "aws_ecs_task_definition" "upload_service" {
  family                   = "upload-service-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "upload-service"
    image     = "${aws_ecr_repository.upload_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8081
      hostPort      = 8081
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
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
        "awslogs-group"         = "/ecs/upload-service-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "upload_service_log" {
  name              = "/ecs/upload-service-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "upload_service" {
  name            = "upload-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.upload_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }
}

resource "aws_ecs_task_definition" "processing_service" {
  family                   = "processing-service-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "processing-service"
    image     = "${aws_ecr_repository.processing_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8082
      hostPort      = 8082
      protocol      = "tcp"
    }]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT_URL", value = "" },
      { name = "REDIS_URL", value = var.redis_url },
      { name = "S3_BUCKET_NAME", value = aws_s3_bucket.diagrams.bucket },
      { name = "SQS_QUEUE_URL", value = aws_sqs_queue.file_uploaded_queue.id },
      { name = "SNS_TOPIC_ARN", value = aws_sns_topic.diagram_processed.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/processing-service-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "processing_service_log" {
  name              = "/ecs/processing-service-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "processing_service" {
  name            = "processing-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.processing_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }
}

resource "aws_ecs_task_definition" "ai_analysis_service" {
  family                   = "ai-analysis-service-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "ai-analysis-service"
    image     = "${aws_ecr_repository.ai_analysis_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8083
      hostPort      = 8083
      protocol      = "tcp"
    }]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT_URL", value = "" },
      { name = "REDIS_URL", value = var.redis_url },
      { name = "SQS_QUEUE_URL", value = aws_sqs_queue.diagram_processed_queue.id },
      { name = "SNS_TOPIC_ARN", value = aws_sns_topic.analysis_completed.arn },
      { name = "OPENAI_MODEL", value = "gpt-4o-mini" },
      { name = "OPENAI_API_KEY", value = var.openai_api_key }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/ai-analysis-service-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "ai_analysis_service_log" {
  name              = "/ecs/ai-analysis-service-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "ai_analysis_service" {
  name            = "ai-analysis-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ai_analysis_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }
}

resource "aws_ecs_task_definition" "report_service" {
  family                   = "report-service-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "report-service"
    image     = "${aws_ecr_repository.report_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8084
      hostPort      = 8084
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
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
        "awslogs-group"         = "/ecs/report-service-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "report_service_log" {
  name              = "/ecs/report-service-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "report_service" {
  name            = "report-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.report_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }
}

resource "aws_ecs_task_definition" "status_service" {
  family                   = "status-service-task-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "status-service"
    image     = "${aws_ecr_repository.status_service.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8085
      hostPort      = 8085
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_HOST", value = aws_db_instance.postgres.address },
      { name = "DB_NAME", value = "status_db" },
      { name = "DB_USER", value = var.db_username },
      { name = "DB_PASS", value = var.db_password },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_ENDPOINT", value = "" },
      { name = "AWS_SQS_FILE_UPLOADED_QUEUE_URL", value = aws_sqs_queue.file_uploaded_queue.id },
      { name = "AWS_SQS_DIAGRAM_PROCESSED_QUEUE_URL", value = aws_sqs_queue.diagram_processed_queue.id },
      { name = "AWS_SQS_ANALYSIS_COMPLETED_QUEUE_URL", value = aws_sqs_queue.analysis_completed_queue.id }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/status-service-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_cloudwatch_log_group" "status_service_log" {
  name              = "/ecs/status-service-${var.environment}"
  retention_in_days = 14
}

resource "aws_ecs_service" "status_service" {
  name            = "status-service-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.status_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_1.id, aws_subnet.private_2.id]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = false
  }
}
