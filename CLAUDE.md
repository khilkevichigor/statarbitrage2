# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a statistical arbitrage trading application built with Spring Boot, Vaadin, and Python integration. The system identifies trading opportunities between correlated cryptocurrency pairs, executes trades via 3Commas API, and provides a web interface for monitoring and control.

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

**Database Layer**
- H2 embedded database for persistence
- JPA repositories for data access
- Database file located at `./data/sa-db`

**Python Integration**
- Python scripts for statistical calculations (`src/main/python/`)
- JEP (Java Embedded Python) library for Python execution
- Z-score and cointegration analysis

**External APIs**
- OKX exchange API for market data
- 3Commas API for trade execution
- Telegram bot for notifications

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

**Web Interface**
- Vaadin-based UI at `src/main/java/com/example/statarbitrage/vaadin/views/MainView.java`
- Real-time dashboard for monitoring trades
- Configuration interface for trading parameters

### Configuration

**Application Properties** (`application.properties`)
- Database: H2 file-based at `./data/sa-db`
- Telegram bot integration
- Vaadin development mode settings

**Trading Settings**
- Configurable via web interface
- Stored in database
- Includes risk parameters, exit strategies, and filters

## Python Dependencies

The application uses Python for statistical calculations. Key Python scripts:
- `calc_zscores.py`: Z-score calculations
- `script.py`: Experimental analysis scripts

## Common Development Patterns

### Adding New Trading Logic
1. Create service classes in `src/main/java/com/example/statarbitrage/services/`
2. Use existing patterns from `ExitStrategyService` and `CointegrationTest`
3. Integrate with the main trading flow via processors

### Database Entities
- Follow existing patterns in `src/main/java/com/example/statarbitrage/model/`
- Use JPA annotations and Lombok for boilerplate reduction
- Repository interfaces extend Spring Data JPA

### API Integration
- HTTP clients in `src/main/java/com/example/statarbitrage/api/`
- Use OkHttp for REST calls
- Gson for JSON serialization

### Frontend Development
- Vaadin components in `src/main/java/com/example/statarbitrage/vaadin/`
- Real-time UI updates using scheduled executors
- Grid-based data presentation

## Important Notes

- The application supports both live trading and simulation modes
- All sensitive configuration (API keys, tokens) should be externalized
- The system uses statistical analysis to identify mean-reverting pairs
- Risk management is built into the exit strategy logic