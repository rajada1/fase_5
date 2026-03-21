# cognito.tf

resource "aws_cognito_user_pool" "pool" {
  name = "architecture-user-pool-${var.environment}"

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }

  auto_verified_attributes = ["email"]
  
  tags = {
    Name = "architecture-user-pool-${var.environment}"
  }
}

resource "aws_cognito_user_pool_client" "client" {
  name         = "architecture-app-client-${var.environment}"
  user_pool_id = aws_cognito_user_pool.pool.id

  generate_secret = false
  
  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH"
  ]
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = "architecture-auth-${var.environment}-${random_string.suffix.result}"
  user_pool_id = aws_cognito_user_pool.pool.id
}

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}
