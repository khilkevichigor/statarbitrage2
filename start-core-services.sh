#!/bin/bash

echo "🚀 Запуск основных микросервисов StatArbitrage..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для проверки порта
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        return 0
    else
        return 1
    fi
}

# Создание директорий для логов и PID файлов
mkdir -p logs pids

# Запуск RabbitMQ через Docker
echo -e "${BLUE}📦 Проверка RabbitMQ...${NC}"
if ! check_port 5672; then
    echo -e "${BLUE}🔄 Запуск RabbitMQ...${NC}"
    docker-compose up -d rabbitmq
    sleep 15
fi

if check_port 5672; then
    echo -e "${GREEN}✅ RabbitMQ готов на порту 5672${NC}"
    echo -e "${GREEN}🌐 Management UI: http://localhost:15672 (admin/admin123)${NC}"
else
    echo -e "${RED}❌ RabbitMQ не запустился${NC}"
    exit 1
fi

# Запуск микросервисов
echo -e "${BLUE}🚀 Запуск микросервисов...${NC}"

# Core Service (8080)
echo -e "${BLUE}🔄 Запуск Core Service...${NC}"
cd core
nohup mvn spring-boot:run > ../logs/core.log 2>&1 &
echo $! > ../pids/core.pid
cd ..
sleep 5

# Notification Service (8081)
echo -e "${BLUE}🔄 Запуск Notification Service...${NC}"
cd notification
nohup mvn spring-boot:run > ../logs/notification.log 2>&1 &
echo $! > ../pids/notification.pid
cd ..
sleep 3

# Statistics Service (8096)
echo -e "${BLUE}🔄 Запуск Statistics Service...${NC}"
cd statistics
nohup mvn spring-boot:run > ../logs/statistics.log 2>&1 &
echo $! > ../pids/statistics.pid
cd ..
sleep 3

# Default Service (8097)
echo -e "${BLUE}🔄 Запуск Default Service...${NC}"
cd default
nohup mvn spring-boot:run > ../logs/default.log 2>&1 &
echo $! > ../pids/default.pid
cd ..
sleep 3

echo -e "${GREEN}🎉 Запуск завершен!${NC}"
echo ""
echo -e "${BLUE}📊 Проверка статуса сервисов:${NC}"
echo "=================================="

services=("core:8080" "notification:8081" "statistics:8096" "default:8097")

for service_port in "${services[@]}"; do
    IFS=':' read -r service port <<< "$service_port"
    
    if check_port $port; then
        echo -e "${GREEN}✅ $service - http://localhost:$port/actuator/health${NC}"
    else
        echo -e "${RED}❌ $service - не запущен на порту $port${NC}"
    fi
done

echo ""
echo -e "${BLUE}🔧 Полезные команды:${NC}"
echo "- Остановить все: ./stop-all.sh"
echo "- Логи Core: tail -f logs/core.log"
echo "- Логи Notification: tail -f logs/notification.log" 
echo "- RabbitMQ UI: http://localhost:15672"

echo ""
echo -e "${YELLOW}💡 Тестирование событий:${NC}"
echo "curl -X POST http://localhost:8097/api/default/test-event"
echo ""