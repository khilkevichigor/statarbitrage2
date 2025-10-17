#!/bin/bash

echo "🚀 Запуск всех микросервисов StatArbitrage..."

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

# Запуск RabbitMQ через Docker
echo -e "${BLUE}📦 Запуск RabbitMQ...${NC}"
docker-compose up -d rabbitmq

# Ожидание запуска RabbitMQ
echo -e "${YELLOW}⏳ Ожидание запуска RabbitMQ...${NC}"
sleep 15

# Проверка RabbitMQ
if check_port 5672; then
    echo -e "${GREEN}✅ RabbitMQ запущен на порту 5672${NC}"
    echo -e "${GREEN}🌐 Management UI доступен: http://localhost:15672 (admin/admin123)${NC}"
else
    echo -e "${RED}❌ RabbitMQ не запустился${NC}"
    exit 1
fi

# Сборка всех модулей
echo -e "${BLUE}🔨 Сборка всех модулей...${NC}"
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Сборка успешна${NC}"
else
    echo -e "${RED}❌ Ошибка сборки${NC}"
    exit 1
fi

# Список микросервисов с портами
declare -A services=(
    ["shared"]="Не запускается (библиотека)"
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
    ["changes"]="8092"
    ["processors"]="8093"
    ["candles"]="8091"
    ["backtesting"]="8094"
    ["chart"]="8095"
    ["statistics"]="8096"
    ["default"]="8097"
)

# Запуск всех сервисов
echo -e "${BLUE}🚀 Запуск микросервисов...${NC}"

for service in "${!services[@]}"; do
    port="${services[$service]}"
    
    if [ "$service" = "shared" ]; then
        echo -e "${YELLOW}📚 $service - ${port}${NC}"
        continue
    fi
    
    echo -e "${BLUE}🔄 Запуск $service на порту $port...${NC}"
    
    # Проверка занятости порта
    if check_port $port; then
        echo -e "${YELLOW}⚠️ Порт $port уже занят, пропускаем $service${NC}"
        continue
    fi
    
    # Запуск сервиса в фоне
    cd $service
    
    # Специальные настройки JVM для candles-service (обработка больших объемов данных)
    if [ "$service" = "candles" ]; then
        export JAVA_OPTS="-Xmx4G -Xms2G -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication"
        echo -e "${BLUE}🔧 Настройки JVM для $service: $JAVA_OPTS${NC}"
    fi
    
    nohup mvn spring-boot:run > ../logs/$service.log 2>&1 &
    echo $! > ../pids/$service.pid
    cd ..
    
    echo -e "${GREEN}✅ $service запускается...${NC}"
    sleep 3
done

# Создание директорий для логов и PID файлов
mkdir -p logs pids

echo -e "${GREEN}🎉 Запуск завершен!${NC}"
echo ""
echo -e "${BLUE}📊 Статус сервисов:${NC}"
echo "=================================="

for service in "${!services[@]}"; do
    port="${services[$service]}"
    
    if [ "$service" = "shared" ]; then
        continue
    fi
    
    if check_port $port; then
        echo -e "${GREEN}✅ $service - http://localhost:$port/actuator/health${NC}"
    else
        echo -e "${RED}❌ $service - не запущен${NC}"
    fi
done

echo ""
echo -e "${BLUE}🔧 Полезные команды:${NC}"
echo "- Остановить все сервисы: ./stop-all.sh"
echo "- Проверить логи: tail -f logs/[service-name].log"
echo "- RabbitMQ Management: http://localhost:15672"
echo ""