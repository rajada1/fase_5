# cognito.tf - AWS Cognito User Pool
# Free Tier: 50,000 MAU free (more than enough for MVP)

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

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true

    string_attribute_constraints {
      min_length = 1
      max_length = 256
    }
  }

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

  # Token validity
  access_token_validity  = 1  # 1 hour
  id_token_validity      = 1  # 1 hour
  refresh_token_validity = 30 # 30 days

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = "arch-auth-${var.environment}-${random_string.cognito_suffix.result}"
  user_pool_id = aws_cognito_user_pool.pool.id
}

resource "random_string" "cognito_suffix" {
  length  = 6
  special = false
  upper   = false
}
