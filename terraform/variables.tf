variable "aws_region" {
  description = "AWS Region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (e.g. dev, prod)"
  type        = string
  default     = "dev"
}

variable "db_username" {
  description = "Database username"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "gemini_api_key" {
  description = "Gemini API key for AI analysis service (provisioned by IADT team)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "cognito_user_pool_id" {
  description = "Cognito User Pool ID (auto-created if empty)"
  type        = string
  default     = ""
}

variable "use_free_tier" {
  description = "Optimize for AWS Free Tier (single-AZ RDS, no NAT, minimal resources)"
  type        = bool
  default     = true
}
