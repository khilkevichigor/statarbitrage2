#!/bin/bash

echo "🛑 Остановка всех микросервисов StatArbitrage..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Список микросервисов
services=("core" "notification" "analytics" "csv" "ui" "database" "cointegration" "trading" "okx" "3commas" "python" "changes" "processors" "candles" "backtesting" "chart" "statistics" "default")

echo -e "${BLUE}🔄 Остановка Spring Boot приложений...${NC}"

# Остановка всех Spring Boot приложений по PID файлам
for service in "${services[@]}"; do
    if [ -f "pids/$service.pid" ]; then
        pid=$(cat pids/$service.pid)
        if kill -0 $pid 2>/dev/null; then
            echo -e "${YELLOW}🛑 Остановка $service (PID: $pid)...${NC}"
            kill $pid
            sleep 2
            
            # Принудительная остановка если процесс все еще жив
            if kill -0 $pid 2>/dev/null; then
                echo -e "${RED}⚠️ Принудительная остановка $service...${NC}"
                kill -9 $pid
            fi
            echo -e "${GREEN}✅ $service остановлен${NC}"
        else
            echo -e "${YELLOW}⚠️ $service уже остановлен${NC}"
        fi
        rm -f pids/$service.pid
    else
        echo -e "${YELLOW}⚠️ PID файл для $service не найден${NC}"
    fi
done

# Остановка всех процессов Java с spring-boot если они остались
echo -e "${BLUE}🧹 Очистка оставшихся Spring Boot процессов...${NC}"
pkill -f "spring-boot:run" 2>/dev/null && echo -e "${GREEN}✅ Оставшиеся процессы остановлены${NC}" || echo -e "${YELLOW}⚠️ Дополнительных процессов не найдено${NC}"

# Остановка RabbitMQ
echo -e "${BLUE}📦 Остановка RabbitMQ...${NC}"
docker-compose down
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ RabbitMQ остановлен${NC}"
else
    echo -e "${YELLOW}⚠️ RabbitMQ уже был остановлен${NC}"
fi

# Очистка директорий
echo -e "${BLUE}🧹 Очистка временных файлов...${NC}"
rm -rf pids/*
echo -e "${GREEN}✅ PID файлы очищены${NC}"

echo ""
echo -e "${GREEN}🎉 Все микросервисы остановлены!${NC}"
echo -e "${BLUE}💡 Логи сохранены в директории logs/${NC}"
echo ""