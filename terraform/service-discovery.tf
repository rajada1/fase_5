# service-discovery.tf - AWS Cloud Map for inter-service communication
# Cloud Map is free (you only pay for DNS queries, which are minimal)

resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "architecture.local"
  description = "Private DNS namespace for ECS services"
  vpc         = aws_vpc.main.id
}

# Service Discovery entries for services that need to be reached by API Gateway
resource "aws_service_discovery_service" "upload_service" {
  name = "upload-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_service_discovery_service" "report_service" {
  name = "report-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_service_discovery_service" "status_service" {
  name = "status-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}
