#!/usr/bin/env bash
set -euo pipefail

# ─── Load .env if it exists (handles files without export prefix) ─────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  set -a
  source "$SCRIPT_DIR/.env"
  set +a
fi

# ─── Map existing .env vars to TF_VAR_ equivalents ───────────────────────────

export TF_VAR_aws_region="${TF_VAR_aws_region:-${AWS_REGION:-}}"
export TF_VAR_app_name="${TF_VAR_app_name:-plany-payment-service}"
export TF_VAR_vpc_id="${TF_VAR_vpc_id:-vpc-03e321dd0cabdf635}"
export TF_VAR_kinesis_stream_name="${TF_VAR_kinesis_stream_name:-${KINESIS_STREAM_NAME:-}}"
export TF_VAR_db_url="${TF_VAR_db_url:-${DB_URL:-}}"
export TF_VAR_db_username="${TF_VAR_db_username:-${DB_USERNAME:-}}"
export TF_VAR_db_password="${TF_VAR_db_password:-${DB_PASSWORD:-}}"
export TF_VAR_stripe_api_key="${TF_VAR_stripe_api_key:-${STRIPE_API_KEY:-}}"
export TF_VAR_stripe_webhook_secret="${TF_VAR_stripe_webhook_secret:-${STRIPE_WEBHOOK_SECRET:-}}"
export TF_VAR_enable_alb="${TF_VAR_enable_alb:-false}"
export TF_VAR_task_cpu="${TF_VAR_task_cpu:-256}"
export TF_VAR_task_memory="${TF_VAR_task_memory:-1024}"

# ─── Validate required vars ───────────────────────────────────────────────────

required_vars=(
  TF_VAR_aws_region
  TF_VAR_kinesis_stream_name
  TF_VAR_db_url
  TF_VAR_db_username
  TF_VAR_db_password
  TF_VAR_stripe_api_key
  TF_VAR_stripe_webhook_secret
)

missing=()
for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    missing+=("$var  (or ${var#TF_VAR_})")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "ERROR: Missing required variables:"
  printf '  %s\n' "${missing[@]}"
  echo ""
  echo "Run: source .env && bash deploy.sh"
  exit 1
fi

REGION="$TF_VAR_aws_region"
APP="$TF_VAR_app_name"
IMAGE_TAG="${IMAGE_TAG:-latest}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "============================================"
echo "  Deploying: $APP"
echo "  Region:    $REGION"
echo "  Account:   $ACCOUNT_ID"
echo "============================================"

echo ""
echo "==> 1. Build JAR"
MVN_CMD=$(command -v ./mvnw 2>/dev/null || command -v mvn)
$MVN_CMD clean package -DskipTests

echo ""
echo "==> 2. Terraform init + apply"
cd terraform
terraform init -upgrade -input=false
terraform apply -auto-approve -input=false
ECR_REPO=$(terraform output -raw ecr_repository_url)
cd ..

echo ""
echo "==> 3. Build & push Docker image to ECR"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

docker build -t "${APP}:${IMAGE_TAG}" .
docker tag "${APP}:${IMAGE_TAG}" "${ECR_REPO}:${IMAGE_TAG}"
docker push "${ECR_REPO}:${IMAGE_TAG}"

echo ""
echo "==> 4. Force ECS to redeploy with new image"
aws ecs update-service \
  --cluster "${APP}-cluster" \
  --service "${APP}-service" \
  --force-new-deployment \
  --region "$REGION" \
  --output text --query "service.serviceName"

echo ""
echo "==> 5. Waiting for service to stabilise (this takes ~2 min)..."
aws ecs wait services-stable \
  --cluster "${APP}-cluster" \
  --services "${APP}-service" \
  --region "$REGION"

echo ""
echo "==> 6. Getting task public IP..."
TASK_ARN=$(aws ecs list-tasks \
  --cluster "${APP}-cluster" \
  --region "$REGION" \
  --query "taskArns[0]" --output text)

ENI_ID=$(aws ecs describe-tasks \
  --cluster "${APP}-cluster" \
  --tasks "$TASK_ARN" \
  --region "$REGION" \
  --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" \
  --output text)

PUBLIC_IP=$(aws ec2 describe-network-interfaces \
  --network-interface-ids "$ENI_ID" \
  --region "$REGION" \
  --query "NetworkInterfaces[0].Association.PublicIp" \
  --output text)

echo ""
echo "============================================"
echo "  Deployed successfully!"
echo ""
echo "  Health:    curl http://${PUBLIC_IP}:8080/actuator/health"
echo "  Swagger:   http://${PUBLIC_IP}:8080/swagger-ui.html"
echo "  Logs:      aws logs tail /ecs/${APP} --follow --region ${REGION}"
echo "============================================"
