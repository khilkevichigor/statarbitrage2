#!/bin/bash

echo "🚀 Запуск всех исправленных микросервисов StatArbitrage..."

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
mkdir -p logs pids data

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

# Сборка всех модулей
echo -e "${BLUE}🔨 Сборка всех модулей...${NC}"
mvn clean install -DskipTests -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Сборка успешна${NC}"
else
    echo -e "${RED}❌ Ошибка сборки${NC}"
    exit 1
fi

# Список микросервисов с портами (все исправленные)
declare -A services=(
    ["core"]="8080"
    ["notification"]="8081"
    ["analytics"]="8082"
    ["csv"]="8083"
    ["ui"]="8084"
    ["database"]="8085"
    ["cointegration"]="8086"
    ["trading"]="8087"
    ["okx"]="8088"
    ["3commas"]="8089"
    ["python"]="8090"
    ["changes"]="8091"
    ["processors"]="8092"
    ["candles"]="8093"
    ["backtesting"]="8094"
    ["chart"]="8095"
    ["statistics"]="8096"
    ["default"]="8097"
)

# Запуск всех сервисов
echo -e "${BLUE}🚀 Запуск микросервисов...${NC}"

for service in "${!services[@]}"; do
    port="${services[$service]}"
    
    echo -e "${BLUE}🔄 Запуск $service на порту $port...${NC}"
    
    # Проверка занятости порта
    if check_port $port; then
        echo -e "${YELLOW}⚠️ Порт $port уже занят, пропускаем $service${NC}"
        continue
    fi
    
    # Запуск сервиса в фоне
    cd $service
    nohup mvn spring-boot:run -q > ../logs/$service.log 2>&1 &
    echo $! > ../pids/$service.pid
    cd ..
    
    echo -e "${GREEN}✅ $service запускается...${NC}"
    sleep 2
done

echo -e "${GREEN}🎉 Запуск завершен!${NC}"
echo ""
echo -e "${BLUE}⏳ Ожидание полного запуска всех сервисов (30 сек)...${NC}"
sleep 30

echo ""
echo -e "${BLUE}📊 Статус сервисов:${NC}"
echo "=================================="

for service in "${!services[@]}"; do
    port="${services[$service]}"
    
    if check_port $port; then
        echo -e "${GREEN}✅ $service - http://localhost:$port/actuator/health${NC}"
    else
        echo -e "${RED}❌ $service - не запущен на порту $port${NC}"
    fi
done

echo ""
echo -e "${BLUE}🔧 Полезные команды:${NC}"
echo "- Остановить все сервисы: ./stop-all.sh"
echo "- Проверить логи: tail -f logs/[service-name].log"
echo "- RabbitMQ Management: http://localhost:15672"
echo ""

echo -e "${YELLOW}💡 Тестирование системы:${NC}"
echo "curl http://localhost:8080/actuator/health"
echo "curl http://localhost:8081/actuator/health"
echo ""