#!/bin/bash
set -e

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$BASE_DIR/.services.pid"
LOG_DIR="$BASE_DIR/logs"

mkdir -p "$LOG_DIR"

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}  Iniciando todos os serviços...${NC}"
echo -e "${GREEN}=====================================${NC}"

# ---- 1. Docker Compose ----
echo -e "\n${YELLOW}[1/5] Subindo docker-compose...${NC}"
docker-compose -f "$BASE_DIR/docker-compose.yml" up -d

# ---- 2. Health checks ----
echo -e "\n${YELLOW}[2/5] Aguardando infraestrutura ficar pronta...${NC}"

wait_for() {
  local name="$1" cmd="$2" retries="${3:-30}" delay="${4:-2}"
  for i in $(seq 1 $retries); do
    if eval "$cmd" > /dev/null 2>&1; then
      echo -e "  ${GREEN}✔ $name pronto${NC}"
      return 0
    fi
    echo -e "  ${YELLOW}⏳ Aguardando $name... ($i/$retries)${NC}"
    sleep "$delay"
  done
  echo -e "  ${RED}✘ $name não ficou pronto a tempo${NC}"
  exit 1
}

wait_for "PostgreSQL" "pg_isready -h 127.0.0.1 -p 5432 -U postgres || docker exec \$(docker-compose -f $BASE_DIR/docker-compose.yml ps -q postgres) pg_isready -U postgres"
wait_for "Redis" "redis-cli -h 127.0.0.1 -p 6379 ping | grep -q PONG || nc -z 127.0.0.1 6379"
wait_for "LocalStack" "curl -sf http://127.0.0.1:4566/_localstack/health > /dev/null 2>&1" 60 2

# ---- 3. Criar .env se não existirem ----
echo -e "\n${YELLOW}[3/5] Verificando arquivos .env...${NC}"

if [ ! -f "$BASE_DIR/processing-service/.env" ]; then
  cat > "$BASE_DIR/processing-service/.env" <<'EOF'
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localhost:4566
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
REDIS_URL=redis://localhost:6379/0
S3_BUCKET_NAME=architecture-diagrams
SQS_QUEUE_URL=http://localhost:4566/000000000000/file-uploaded-queue
SNS_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:diagram-processed-topic
EOF
  echo -e "  ${GREEN}✔ processing-service/.env criado${NC}"
else
  echo -e "  ${YELLOW}⏭ processing-service/.env já existe${NC}"
fi

if [ ! -f "$BASE_DIR/ai-analysis-service/.env" ]; then
  cat > "$BASE_DIR/ai-analysis-service/.env" <<'EOF'
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localhost:4566
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
REDIS_URL=redis://localhost:6379/0
GEMINI_API_KEY=COLOQUE_SUA_CHAVE_AQUI
GEMINI_MODEL=gemini-2.0-flash
SQS_QUEUE_URL=http://localhost:4566/000000000000/diagram-processed-queue
SNS_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:analysis-completed-topic
EOF
  echo -e "  ${GREEN}✔ ai-analysis-service/.env criado${NC}"
else
  echo -e "  ${YELLOW}⏭ ai-analysis-service/.env já existe${NC}"
fi

# ---- 4. Iniciar microsserviços ----
echo -e "\n${YELLOW}[4/5] Iniciando microsserviços...${NC}"

# Limpar PIDs anteriores
> "$PID_FILE"

start_java_service() {
  local name="$1" port="$2" extra_env="$3"
  echo -e "  Iniciando ${GREEN}$name${NC} na porta $port..."
  (
    cd "$BASE_DIR/$name"
    export SPRING_PROFILES_ACTIVE=local
    export SERVER_PORT="$port"
    [ -n "$extra_env" ] && eval "export $extra_env"
    mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=$port" > "$LOG_DIR/$name.log" 2>&1
  ) &
  echo "$! $name" >> "$PID_FILE"
}

start_python_service() {
  local name="$1" port="$2"
  echo -e "  Iniciando ${GREEN}$name${NC} na porta $port..."
  (
    cd "$BASE_DIR/$name"
    set -a; [ -f .env ] && source .env; set +a
    pip install -r requirements.txt > "$LOG_DIR/$name-pip.log" 2>&1
    uvicorn app.main:app --host 0.0.0.0 --port "$port" > "$LOG_DIR/$name.log" 2>&1
  ) &
  echo "$! $name" >> "$PID_FILE"
}

start_java_service "api-gateway" 8080 "ALLOW_INSECURE_LOCAL_API=true"
start_java_service "upload-service" 8081
start_python_service "processing-service" 8082
start_python_service "ai-analysis-service" 8083
start_java_service "report-service" 8084
start_java_service "status-service" 8085

# ---- 5. Status ----
echo -e "\n${YELLOW}[5/5] Status dos serviços${NC}"
echo -e "${GREEN}=====================================${NC}"
echo -e "  api-gateway         → http://localhost:8080"
echo -e "  upload-service      → http://localhost:8081"
echo -e "  processing-service  → http://localhost:8082"
echo -e "  ai-analysis-service → http://localhost:8083"
echo -e "  report-service      → http://localhost:8084"
echo -e "  status-service      → http://localhost:8085"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "  📁 Logs em:  $LOG_DIR/"
echo -e "  📋 PIDs em:  $PID_FILE"
echo ""
echo -e "  Para parar tudo: ${YELLOW}./stop-all.sh${NC}"
echo ""
echo -e "${GREEN}Todos os serviços foram iniciados em background!${NC}"
