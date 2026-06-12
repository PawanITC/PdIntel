variable "aws_region" {
  description = "AWS region — TF_VAR_aws_region"
}

variable "app_name" {
  description = "Application name used for all resource names — TF_VAR_app_name"
}

variable "vpc_id" {
  description = "VPC ID to deploy into — TF_VAR_vpc_id"
}

variable "kinesis_stream_name" {
  description = "Kinesis stream name — TF_VAR_kinesis_stream_name"
}

variable "db_url" {
  description = "Full JDBC URL of the existing RDS instance — TF_VAR_db_url"
  sensitive   = true
}

variable "db_username" {
  description = "DB username — TF_VAR_db_username"
  sensitive   = true
}

variable "db_password" {
  description = "DB password — TF_VAR_db_password"
  sensitive   = true
}

variable "stripe_api_key" {
  description = "Stripe API key — TF_VAR_stripe_api_key"
  sensitive   = true
}

variable "stripe_webhook_secret" {
  description = "Stripe webhook signing secret — TF_VAR_stripe_webhook_secret"
  sensitive   = true
}

variable "cognito_issuer_uri" {
  description = "Cognito issuer URI — TF_VAR_cognito_issuer_uri (optional)"
  default     = ""
}

variable "enable_alb" {
  description = "Set to true to provision an ALB (~$18/month). False = access via task public IP. TF_VAR_enable_alb"
  default     = false
}

variable "task_cpu" {
  description = "Fargate task CPU units (256/512/1024) — TF_VAR_task_cpu"
  default     = "256"
}

variable "task_memory" {
  description = "Fargate task memory in MB (512/1024/2048) — TF_VAR_task_memory"
  default     = "1024"
}
