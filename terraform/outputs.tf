output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "alb_dns_name" {
  value       = var.enable_alb ? aws_lb.app[0].dns_name : "ALB disabled — use task public IP"
  description = "ALB DNS (only set when enable_alb=true)"
}

output "kinesis_stream_arn" {
  value = aws_kinesis_stream.payments.arn
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.app.name
}
