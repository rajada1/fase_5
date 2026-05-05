# resources.tf - S3, SNS, SQS, RDS, ECR
# All messaging services (SNS/SQS) are within Free Tier limits for MVP usage.

# ============================================================
# S3 Bucket (5GB free for 12 months)
# ============================================================
resource "aws_s3_bucket" "diagrams" {
  bucket = "architecture-diagrams-${var.environment}-${random_string.bucket_suffix.result}"

  tags = {
    Name = "architecture-diagrams-${var.environment}"
  }
}

resource "random_string" "bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

resource "aws_s3_bucket_public_access_block" "diagrams" {
  bucket = aws_s3_bucket.diagrams.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ============================================================
# SNS Topics (1M publishes/month free)
# ============================================================
resource "aws_sns_topic" "file_uploaded" {
  name = "file-uploaded-topic-${var.environment}"
}

resource "aws_sns_topic" "diagram_processed" {
  name = "diagram-processed-topic-${var.environment}"
}

resource "aws_sns_topic" "analysis_completed" {
  name = "analysis-completed-topic-${var.environment}"
}

# ============================================================
# SQS Queues (1M requests/month free)
# ============================================================

# Dead Letter Queues
resource "aws_sqs_queue" "file_uploaded_dlq" {
  name                      = "file-uploaded-dlq-${var.environment}"
  message_retention_seconds = 1209600 # 14 days
}

resource "aws_sqs_queue" "diagram_processed_dlq" {
  name                      = "diagram-processed-dlq-${var.environment}"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "analysis_completed_dlq" {
  name                      = "analysis-completed-dlq-${var.environment}"
  message_retention_seconds = 1209600
}

# Main Queues
resource "aws_sqs_queue" "file_uploaded_queue" {
  name                       = "file-uploaded-queue-${var.environment}"
  visibility_timeout_seconds = 300
  receive_wait_time_seconds  = 20 # Long polling (reduces API calls)

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.file_uploaded_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "diagram_processed_queue" {
  name                       = "diagram-processed-queue-${var.environment}"
  visibility_timeout_seconds = 600 # AI analysis can take longer
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.diagram_processed_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "analysis_completed_queue" {
  name                       = "analysis-completed-queue-${var.environment}"
  visibility_timeout_seconds = 300
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.analysis_completed_dlq.arn
    maxReceiveCount     = 3
  })
}

# Status Service dedicated queues (fanout from same topics)
resource "aws_sqs_queue" "status_file_uploaded_queue" {
  name                       = "status-file-uploaded-queue-${var.environment}"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.file_uploaded_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "status_diagram_processed_queue" {
  name                       = "status-diagram-processed-queue-${var.environment}"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.diagram_processed_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "status_analysis_completed_queue" {
  name                       = "status-analysis-completed-queue-${var.environment}"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.analysis_completed_dlq.arn
    maxReceiveCount     = 3
  })
}

# ============================================================
# Queue Policies (allow SNS to publish to SQS)
# ============================================================
data "aws_iam_policy_document" "file_uploaded_queue_policy_doc" {
  statement {
    sid    = "AllowSNSPublish"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.file_uploaded_queue.arn, aws_sqs_queue.status_file_uploaded_queue.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.file_uploaded.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "file_uploaded_queue_policy" {
  queue_url = aws_sqs_queue.file_uploaded_queue.id
  policy    = data.aws_iam_policy_document.file_uploaded_queue_policy_doc.json
}

resource "aws_sqs_queue_policy" "status_file_uploaded_queue_policy" {
  queue_url = aws_sqs_queue.status_file_uploaded_queue.id
  policy    = data.aws_iam_policy_document.file_uploaded_queue_policy_doc.json
}

data "aws_iam_policy_document" "diagram_processed_queue_policy_doc" {
  statement {
    sid    = "AllowSNSPublish"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.diagram_processed_queue.arn, aws_sqs_queue.status_diagram_processed_queue.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.diagram_processed.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "diagram_processed_queue_policy" {
  queue_url = aws_sqs_queue.diagram_processed_queue.id
  policy    = data.aws_iam_policy_document.diagram_processed_queue_policy_doc.json
}

resource "aws_sqs_queue_policy" "status_diagram_processed_queue_policy" {
  queue_url = aws_sqs_queue.status_diagram_processed_queue.id
  policy    = data.aws_iam_policy_document.diagram_processed_queue_policy_doc.json
}

data "aws_iam_policy_document" "analysis_completed_queue_policy_doc" {
  statement {
    sid    = "AllowSNSPublish"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.analysis_completed_queue.arn, aws_sqs_queue.status_analysis_completed_queue.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.analysis_completed.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "analysis_completed_queue_policy" {
  queue_url = aws_sqs_queue.analysis_completed_queue.id
  policy    = data.aws_iam_policy_document.analysis_completed_queue_policy_doc.json
}

resource "aws_sqs_queue_policy" "status_analysis_completed_queue_policy" {
  queue_url = aws_sqs_queue.status_analysis_completed_queue.id
  policy    = data.aws_iam_policy_document.analysis_completed_queue_policy_doc.json
}

# ============================================================
# SNS -> SQS Subscriptions
# ============================================================

# Processing service consumes file-uploaded events
resource "aws_sns_topic_subscription" "file_uploaded_to_processing" {
  topic_arn = aws_sns_topic.file_uploaded.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.file_uploaded_queue.arn
}

# Status service also consumes file-uploaded events (fanout)
resource "aws_sns_topic_subscription" "file_uploaded_to_status" {
  topic_arn = aws_sns_topic.file_uploaded.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.status_file_uploaded_queue.arn
}

# AI analysis service consumes diagram-processed events
resource "aws_sns_topic_subscription" "diagram_processed_to_ai" {
  topic_arn = aws_sns_topic.diagram_processed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.diagram_processed_queue.arn
}

# Status service also consumes diagram-processed events (fanout)
resource "aws_sns_topic_subscription" "diagram_processed_to_status" {
  topic_arn = aws_sns_topic.diagram_processed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.status_diagram_processed_queue.arn
}

# Report service consumes analysis-completed events
resource "aws_sns_topic_subscription" "analysis_completed_to_report" {
  topic_arn = aws_sns_topic.analysis_completed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.analysis_completed_queue.arn
}

# Status service also consumes analysis-completed events (fanout)
resource "aws_sns_topic_subscription" "analysis_completed_to_status" {
  topic_arn = aws_sns_topic.analysis_completed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.status_analysis_completed_queue.arn
}

# ============================================================
# RDS PostgreSQL (db.t3.micro - 750h/month free for 12 months)
# ============================================================
resource "aws_db_instance" "postgres" {
  identifier = "architecture-db-${var.environment}"

  engine         = "postgres"
  engine_version = "15.12"
  instance_class = "db.t3.micro"

  allocated_storage     = 20 # 20GB free tier
  max_allocated_storage = 20 # Disable autoscaling to stay in free tier
  storage_type          = "gp2"

  db_name  = "architecturedb"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.rds_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  # Free Tier optimizations
  multi_az            = false # Single-AZ for free tier
  publicly_accessible = false

  skip_final_snapshot      = true # Dev environment - skip final snapshot
  backup_retention_period  = 1    # Minimum backup (free)
  delete_automated_backups = true
  copy_tags_to_snapshot    = true

  # Performance Insights disabled (not free tier)
  performance_insights_enabled = false

  tags = {
    Name = "architecture-db-${var.environment}"
  }
}

# ============================================================
# ECR Repositories (500MB free storage)
# ============================================================
resource "aws_ecr_repository" "api_gateway" {
  name                 = "architecture/api-gateway"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false # Disable to save costs
  }
}

resource "aws_ecr_repository" "upload_service" {
  name                 = "architecture/upload-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_ecr_repository" "processing_service" {
  name                 = "architecture/processing-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_ecr_repository" "ai_analysis_service" {
  name                 = "architecture/ai-analysis-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_ecr_repository" "report_service" {
  name                 = "architecture/report-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_ecr_repository" "status_service" {
  name                 = "architecture/status-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

# ECR Lifecycle Policy - keep only last 3 images to save storage
resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each = toset([
    aws_ecr_repository.api_gateway.name,
    aws_ecr_repository.upload_service.name,
    aws_ecr_repository.processing_service.name,
    aws_ecr_repository.ai_analysis_service.name,
    aws_ecr_repository.report_service.name,
    aws_ecr_repository.status_service.name,
  ])

  repository = each.value

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only last 3 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 3
      }
      action = {
        type = "expire"
      }
    }]
  })
}
