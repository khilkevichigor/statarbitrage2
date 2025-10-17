# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Language

- Always communicate in Russian language (Русский язык)
- All responses, explanations, and interactions should be in Russian

## Project Overview

This is a statistical arbitrage trading system built with **microservices architecture** using Spring Cloud. The system identifies trading opportunities between correlated cryptocurrency pairs, executes trades via OKX API, and provides a web interface for monitoring and control.

## Repository Information

- **GitHub Repository**: https://github.com/khilkevichigor/statarbitrage2
- **Architecture**: Spring Cloud microservices
- **Database**: PostgreSQL with Docker Compose
- **Message Broker**: RabbitMQ
- **Deployment**: Docker Compose with separate services

## Build and Development Commands

### Java/Spring Boot

- **Build all**: `mvn clean install`
- **Run core service**: `cd core && mvn spring-boot:run`
- **Run with Docker**: `docker-compose up`
- **Test**: `mvn test`
- **Package**: `mvn clean package`

### Docker Compose

- **Start all services**: `docker-compose up -d`
- **Stop all services**: `docker-compose down`
- **View logs**: `docker-compose logs -f [service-name]`
- **Rebuild**: `docker-compose up --build`

### Frontend (Vaadin)

- **Install dependencies**: `npm install`
- **Build frontend**: `npx vite build`
- **Development mode**: The Vaadin frontend is automatically built during Spring Boot startup

## Architecture Overview

### Microservices Architecture

**Core Service** (`core/` - port 8181)
- Main trading logic and Vaadin UI
- Trading processors and schedulers
- Portfolio management
- Risk management and exit strategies

**Cointegration Service** (`cointegration/` - port 8182)
- Statistical pair analysis
- Z-score calculations
- Pair discovery and validation

**Candles Service** (`candles/` - port 8183)
- Market data collection and caching
- OKX API integration for price data
- Candle data processing

**Notification Service** (`notification/` - port 8184)
- Telegram bot integration
- Event-driven notifications
- Message formatting and delivery

**Analytics Service** (`analytics/` - port 8185)
- Performance analytics
- Trade statistics
- Reporting functionality

**Infrastructure Services**
- PostgreSQL database (port 5432)
- RabbitMQ message broker (port 5672, management 15672)
- pgAdmin database management (port 8080)

**Database Layer**

- PostgreSQL database for persistence
- JPA repositories for data access
- Database runs in Docker container
- Connection: localhost:5432/statarbitrage (user: statuser, pass: statpass123)
- Flyway migrations for schema management

**Python Integration**

- External Python REST API for statistical calculations
- Cointegration analysis API at `http://localhost:8000`
- `PythonRestClient` for API communication
- Health check service for Python API availability

**External APIs**

- OKX exchange API for market data and trade execution (using OKX v5 Java SDK)
- Telegram bot for notifications
- GeolocationService for VPN/IP location checking (protects against US IP addresses)

### Key Services

**Trading Flow Services**

- `FetchPairsProcessor`: Identifies trading pairs using statistical analysis
- `StartNewTradeProcessor`: Initiates new trades
- `UpdateTradeProcessor`: Monitors and updates active trades
- `TradeAndSimulationScheduler`: Automated trading scheduler

**Data Services**

- `PairDataService`: Manages trading pair data
- `CandlesService`: Handles market data
- `SettingsService`: Application configuration
- `StatisticsService`: Trading performance metrics
- `TradeLogService`: Trade history management

**Analysis Services**

- `ZScoreService`: Z-score calculations and analysis
- `ExitStrategyService`: Exit strategy implementations
- `ValidateService`: Data validation
- `ChangesService`: Price change analysis

**Communication Services**

- `EventSendService`: Event publishing
- `UIUpdateService`: Real-time UI updates
- `ChartService`: Chart generation
- `GeolocationService`: IP geolocation checking for OKX API protection

**Web Interface**

- Vaadin-based UI in `src/main/java/com/example/statarbitrage/ui/`
- `MainView.java`: Main dashboard
- `SettingsComponent.java`: Configuration interface
- `StatisticsComponent.java`: Performance metrics view
- `TradingPairsComponent.java`: Trading pairs management

### Configuration

**Application Configuration** (`application.yml`)

- Database: PostgreSQL connection to Docker container
- RabbitMQ messaging configuration
- Service discovery and inter-service communication
- Telegram bot integration
- OKX API configuration
- Flyway database migrations

**Trading Settings**

- Configurable via web interface
- Stored in database
- Includes risk parameters, exit strategies, and filters

## Dependencies

### Key Libraries

- **Spring Boot 3.5.0**: Core framework
- **Spring Cloud**: Microservices framework
- **Spring Cloud Stream**: Event-driven messaging
- **Vaadin 24.3.9**: Frontend framework
- **PostgreSQL**: Primary database
- **RabbitMQ**: Message broker
- **Flyway**: Database migrations
- **OpenFeign**: Inter-service communication
- **OKX v5 Java SDK**: Exchange API integration
- **Telegram Bots**: Bot integration
- **OkHttp**: HTTP client
- **Gson**: JSON serialization
- **JFreeChart & XChart**: Chart generation
- **TA4J**: Technical analysis
- **Apache Commons Math**: Mathematical computations
- **Lombok**: Boilerplate reduction

### Testing

- **JUnit 5**: Testing framework
- **Spring Boot Test**: Integration testing

## Project Structure

```
├── shared/                              # Shared components across microservices
│   ├── dto/                            # Data transfer objects
│   ├── events/                         # Event definitions
│   ├── models/                         # Domain models
│   ├── enums/                          # Shared enums
│   └── utils/                          # Utility classes
├── core/                               # Core trading service (port 8181)
│   ├── src/main/java/com/example/core/
│   │   ├── CoreApplication.java        # Main application entry point
│   │   ├── processors/                 # Trading flow processors
│   │   ├── repositories/               # Data access layer
│   │   ├── schedulers/                 # Scheduled tasks
│   │   ├── services/                   # Business services
│   │   ├── trading/                    # Trading system components
│   │   └── ui/                         # Vaadin UI components
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/               # Flyway migrations
├── cointegration/                      # Statistical analysis service (port 8182)
│   ├── src/main/java/com/example/cointegration/
│   │   ├── CointegrationApplication.java
│   │   ├── processors/                 # Analysis processors
│   │   ├── services/                   # Statistical services
│   │   └── schedulers/                 # Analysis schedulers
├── candles/                            # Market data service (port 8183)
│   ├── src/main/java/com/example/candles/
│   │   ├── CandlesApplication.java
│   │   ├── client/                     # OKX API client
│   │   ├── service/                    # Data processing services
│   │   └── repositories/               # Cache repositories
├── notification/                       # Notification service (port 8184)
│   ├── src/main/java/com/example/notification/
│   │   ├── NotificationApplication.java
│   │   ├── bot/                        # Telegram bot implementation
│   │   └── service/                    # Notification services
├── analytics/                          # Analytics service (port 8185)
│   ├── src/main/java/com/example/analytics/
│   │   ├── AnalyticsApplication.java
│   │   ├── controller/                 # Analytics endpoints
│   │   └── service/                    # Analytics services
├── docker-compose.yml                  # Docker services configuration
├── application-global.yml              # Global configuration
└── start-all.sh                        # Service startup script
```

## Common Development Patterns

### Adding New Trading Logic

1. Create service classes in `core/src/main/java/com/example/core/services/`
2. Use existing patterns from `ExitStrategyService` and other services
3. Integrate with the main trading flow via processors
4. Use events for inter-service communication

### Database Entities

- Follow existing patterns in `shared/src/main/java/com/example/shared/models/`
- Use JPA annotations and Lombok for boilerplate reduction
- Repository interfaces extend Spring Data JPA
- Use Flyway migrations for schema changes

### Inter-Service Communication

- Use OpenFeign for synchronous calls between services
- Use RabbitMQ events for asynchronous communication
- Follow event-driven architecture patterns
- Use `shared` module for DTOs and events

### Frontend Development

- Vaadin components in `core/src/main/java/com/example/core/ui/`
- Real-time UI updates using scheduled executors
- Grid-based data presentation
- Integration with backend services via REST endpoints

## Important Notes

- The system supports both live trading and simulation modes
- All sensitive configuration (API keys, tokens) should be externalized via environment variables
- The system uses statistical analysis to identify mean-reverting pairs
- Risk management is built into the exit strategy logic
- Python integration is handled via REST API calls rather than embedded Python
- Database schema managed by Flyway migrations (ddl-auto=update)
- Vaadin frontend is automatically built during Spring Boot startup
- Services communicate via RabbitMQ events and OpenFeign REST calls
- Each microservice has its own responsibility and can be scaled independently
- Docker Compose orchestrates all services for easy deployment

## GeolocationService Details

The `GeolocationService` is a critical security component that protects against accidental API calls from US IP addresses when VPN is disconnected.

### Features

- **IP Geolocation Checking**: Verifies current IP location before OKX API calls
- **Caching**: 5-minute cache to avoid excessive API calls
- **Multiple Service Fallbacks**: Uses ip-api.com, ipify.org, and ipgeolocation.io
- **VPN Protection**: Blocks all OKX API calls if IP is detected as US-based
- **Comprehensive Logging**: Detailed logging for debugging and monitoring

### Integration Points

- **RealOkxTradingProvider**: All OKX API methods check geolocation before execution
- **StatarbitrageApplication**: Startup geolocation check with warnings
- **Centralized Service**: Eliminates code duplication between components

### Usage

```java
// Check if current IP location allows OKX API calls
if(!geolocationService.isGeolocationAllowed()){
    log.error("❌ БЛОКИРОВКА: OKX API заблокирован из-за геолокации!");
    return;
}
```

### Configuration

- No additional configuration required
- Uses public geolocation APIs
- Gracefully handles API failures (allows trading to continue)
- Startup check with critical warnings if US IP detected