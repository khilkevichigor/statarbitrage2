# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Language
- Always communicate in Russian language (Русский язык)
- All responses, explanations, and interactions should be in Russian

## Project Overview

This is a statistical arbitrage trading application built with Spring Boot, Vaadin, and Python integration. The system identifies trading opportunities between correlated cryptocurrency pairs, executes trades via OKX API, and provides a web interface for monitoring and control.

## Build and Development Commands

### Java/Spring Boot
- **Build**: `mvn clean install`
- **Run application**: `mvn spring-boot:run`
- **Test**: `mvn test`
- **Package**: `mvn clean package`

### Frontend (Vaadin)
- **Install dependencies**: `npm install`
- **Build frontend**: `npx vite build`
- **Development mode**: The Vaadin frontend is automatically built during Spring Boot startup

## Architecture Overview

### Core Components

**Spring Boot Application** (`StatarbitrageApplication.java`)
- Main entry point with async and scheduling enabled
- Runs on port 8181 by default
- WAR packaging for deployment

**Database Layer**
- SQLite embedded database for persistence
- JPA repositories for data access
- Database file located at `./data/sa-db.sqlite`
- Uses Hibernate Community SQLite dialect

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

**Application Properties** (`application.properties`)
- Database: SQLite file-based at `./data/sa-db.sqlite`
- Telegram bot integration with configured token
- Vaadin development mode settings
- Cointegration API URL configuration

**Trading Settings**
- Configurable via web interface
- Stored in database
- Includes risk parameters, exit strategies, and filters

## Dependencies

### Key Libraries
- **Spring Boot 3.5.0**: Core framework
- **Vaadin 24.3.9**: Frontend framework
- **OKX v5 Java SDK**: Exchange API integration
- **SQLite Database**: Embedded database with Hibernate Community dialects
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
src/main/java/com/example/statarbitrage/
├── StatarbitrageApplication.java          # Main application entry point
├── ServletInitializer.java                # WAR deployment configuration
├── bot/                                   # Telegram bot implementation
│   ├── BotConfig.java
│   ├── BotInitializer.java
│   ├── BotMenu.java
│   └── TelegramBot.java
├── client_okx/                           # OKX exchange client
│   └── OkxClient.java
├── client_python/                        # Python API client
│   ├── CointegrationApiHealthCheck.java
│   └── PythonRestClient.java
├── common/                               # Shared components
│   ├── constant/Constants.java
│   ├── dto/                             # Data transfer objects
│   ├── events/                          # Event definitions
│   ├── model/                           # Domain models
│   └── utils/                           # Utility classes
├── core/                                # Core business logic
│   ├── processors/                      # Trading flow processors
│   ├── repositories/                    # Data access layer
│   ├── schedulers/                      # Scheduled tasks
│   └── services/                        # Business services
├── trading/                             # Trading system components
│   ├── interfaces/                      # Trading provider interfaces
│   ├── model/                          # Trading domain models
│   ├── providers/                      # Trading provider implementations
│   └── services/                       # Trading services (including GeolocationService)
└── ui/                                  # Vaadin UI components
    ├── AppShell.java
    ├── MainView.java
    ├── SettingsComponent.java
    ├── StatisticsComponent.java
    └── TradingPairsComponent.java
```

## Common Development Patterns

### Adding New Trading Logic
1. Create service classes in `src/main/java/com/example/statarbitrage/core/services/`
2. Use existing patterns from `ExitStrategyService` and other services
3. Integrate with the main trading flow via processors

### Database Entities
- Follow existing patterns in `src/main/java/com/example/statarbitrage/common/model/`
- Use JPA annotations and Lombok for boilerplate reduction
- Repository interfaces extend Spring Data JPA

### API Integration
- HTTP clients in `src/main/java/com/example/statarbitrage/client_*/`
- Use OkHttp for REST calls
- Gson for JSON serialization

### Frontend Development
- Vaadin components in `src/main/java/com/example/statarbitrage/ui/`
- Real-time UI updates using scheduled executors
- Grid-based data presentation

## Important Notes

- The application supports both live trading and simulation modes
- All sensitive configuration (API keys, tokens) should be externalized
- The system uses statistical analysis to identify mean-reverting pairs
- Risk management is built into the exit strategy logic
- Python integration is handled via REST API calls rather than embedded Python
- Database auto-creates schema on startup (ddl-auto=create)
- Vaadin frontend is automatically built during Spring Boot startup

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
if (!geolocationService.isGeolocationAllowed()) {
    log.error("🚫 БЛОКИРОВКА: OKX API заблокирован из-за геолокации!");
    return;
}
```

### Configuration
- No additional configuration required
- Uses public geolocation APIs
- Gracefully handles API failures (allows trading to continue)
- Startup check with critical warnings if US IP detected