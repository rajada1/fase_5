#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$BASE_DIR/.services.pid"

echo -e "${RED}=====================================${NC}"
echo -e "${RED}  Parando todos os serviços...${NC}"
echo -e "${RED}=====================================${NC}"

# Parar microsserviços via PID file
if [ -f "$PID_FILE" ]; then
  while IFS=' ' read -r pid name; do
    if kill -0 "$pid" 2>/dev/null; then
      echo -e "  Parando ${GREEN}$name${NC} (PID $pid)..."
      kill "$pid" 2>/dev/null
      # Matar processos filhos (mvn/java/python)
      pkill -P "$pid" 2>/dev/null
    else
      echo -e "  ${RED}$name (PID $pid) já não está rodando${NC}"
    fi
  done < "$PID_FILE"
  rm -f "$PID_FILE"
else
  echo -e "  ${RED}Arquivo de PIDs não encontrado${NC}"
fi

# Parar docker-compose
echo -e "\n  Parando docker-compose..."
docker-compose -f "$BASE_DIR/docker-compose.yml" down

echo -e "\n${GREEN}Todos os serviços foram parados!${NC}"
