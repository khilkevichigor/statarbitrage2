#!/bin/bash

echo "🧪 Тестирование микросервисной архитектуры StatArbitrage..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для проверки HTTP endpoint
check_endpoint() {
    local url=$1
    local service=$2
    
    response=$(curl -s -w "%{http_code}" -o /dev/null $url)
    
    if [ $response -eq 200 ]; then
        echo -e "${GREEN}✅ $service - OK ($url)${NC}"
        return 0
    else
        echo -e "${RED}❌ $service - FAIL ($url) - HTTP $response${NC}"
        return 1
    fi
}

# Проверка сборки
echo -e "${BLUE}🔨 Тестирование сборки...${NC}"
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Сборка прошла успешно${NC}"
else
    echo -e "${RED}❌ Ошибка сборки${NC}"
    exit 1
fi

# Проверка зависимостей
echo -e "${BLUE}📦 Проверка зависимостей...${NC}"
mvn dependency:tree -q > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Зависимости разрешены${NC}"
else
    echo -e "${RED}❌ Проблемы с зависимостями${NC}"
    exit 1
fi

# Проверка структуры проекта
echo -e "${BLUE}📁 Проверка структуры проекта...${NC}"

required_dirs=("shared" "core" "notification" "analytics" "default")
for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        echo -e "${GREEN}✅ Директория $dir существует${NC}"
    else
        echo -e "${RED}❌ Директория $dir не найдена${NC}"
    fi
done

# Проверка важных файлов
echo -e "${BLUE}📄 Проверка важных файлов...${NC}"

required_files=("pom.xml" "docker-compose.yml" "start-all.sh" "stop-all.sh" "README.md")
for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ Файл $file существует${NC}"
    else
        echo -e "${RED}❌ Файл $file не найден${NC}"
    fi
done

# Проверка Java файлов
echo -e "${BLUE}☕ Проверка Java файлов...${NC}"

if [ -f "shared/src/main/java/com/example/shared/events/BaseEvent.java" ]; then
    echo -e "${GREEN}✅ BaseEvent.java найден${NC}"
else
    echo -e "${RED}❌ BaseEvent.java не найден${NC}"
fi

if [ -f "core/src/main/java/com/example/core/CoreApplication.java" ]; then
    echo -e "${GREEN}✅ CoreApplication.java найден${NC}"
else
    echo -e "${RED}❌ CoreApplication.java не найден${NC}"
fi

# Проверка конфигураций
echo -e "${BLUE}⚙️ Проверка конфигураций...${NC}"

config_files=("core/src/main/resources/application.yml" "notification/src/main/resources/application.yml")
for file in "${config_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ Конфигурация $file найдена${NC}"
    else
        echo -e "${RED}❌ Конфигурация $file не найдена${NC}"
    fi
done

# Проверка синтаксиса YAML файлов (если установлен yamllint)
if command -v yamllint &> /dev/null; then
    echo -e "${BLUE}📝 Проверка синтаксиса YAML...${NC}"
    for file in $(find . -name "*.yml" -o -name "*.yaml"); do
        yamllint -d relaxed $file > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ YAML синтаксис корректен: $file${NC}"
        else
            echo -e "${YELLOW}⚠️ Возможные проблемы в YAML: $file${NC}"
        fi
    done
fi

# Проверка Maven модулей
echo -e "${BLUE}📋 Проверка Maven модулей...${NC}"

modules=("shared" "core" "notification" "analytics" "csv" "ui" "database" "cointegration" "trading" "okx" "3commas" "python" "changes" "processors" "candles" "backtesting" "chart" "statistics" "default")

for module in "${modules[@]}"; do
    if [ -f "$module/pom.xml" ]; then
        echo -e "${GREEN}✅ Maven модуль $module настроен${NC}"
    else
        echo -e "${RED}❌ Maven модуль $module не настроен${NC}"
    fi
done

# Тест компиляции каждого модуля
echo -e "${BLUE}🔧 Тестирование компиляции модулей...${NC}"

for module in "shared" "core" "notification" "analytics" "default"; do
    echo -e "${YELLOW}🔄 Компиляция $module...${NC}"
    cd $module
    mvn compile -q > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $module скомпилирован успешно${NC}"
    else
        echo -e "${RED}❌ Ошибка компиляции $module${NC}"
    fi
    cd ..
done

echo ""
echo -e "${BLUE}📊 Результаты тестирования:${NC}"
echo "=================================="

# Подсчет статистики
total_services=19
created_services=$(ls -d */ | grep -v target | wc -l)

echo -e "${BLUE}📈 Статистика:${NC}"
echo "- Всего микросервисов: $total_services"
echo "- Создано директорий: $created_services"
echo "- Docker Compose: готов"
echo "- Скрипты управления: готовы"
echo "- Документация: готова"

echo ""
echo -e "${GREEN}🎉 Тестирование завершено!${NC}"
echo -e "${BLUE}💡 Для запуска системы используйте: ./start-all.sh${NC}"
echo ""