terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  # Endpoints configuration to point to LocalStack when testing locally
  # In a real AWS environment, these are usually omitted unless overriding
  # Example for LocalStack:
  # endpoints {
  #   s3  = "http://localhost:4566"
  #   sns = "http://localhost:4566"
  #   sqs = "http://localhost:4566"
  # }
}
