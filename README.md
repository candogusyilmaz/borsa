# Portfolio & Analytics Platform

A personal finance and investment tracking platform that allows users to record their trades and analyze performance
across multiple portfolios and dashboards.

This project is actively evolving and focuses on real-world architecture, deployment, and scalability practices.

The platform is continuously being refactored to improve code quality,
maintainability, and user experience.

---

## 🚀 Features

- Authentication
    - Google OAuth login
    - Custom email/password login

- Trade Management
    - Buy / Sell transaction tracking
    - Support for multiple instruments (stocks, crypto, metals, etc.)
    - Support for multiple currencies
    - Multi-portfolio support

- Analytics
    - Total balance tracking
    - Daily change calculation
    - Realized P&L (monthly)
    - Cumulative and individual trade profit/loss charts
    - Performance dashboards linked to portfolios

- UI / UX (still refactoring)
    - Light / Dark mode
    - Responsive interface
    - Multiple dashboard views

- Infrastructure
    - Dockerized deployment
    - Automated builds via Dokploy
    - Environment-based configuration
    - Secure secrets management

- Market Data Integration
    - Automated data ingestion from multiple providers (Forex, BIST, upcoming crypto APIs...)
    - Dynamic adapter system for new instruments
    - Scheduled price updates
    - Generic DTO-based integration layer
    - AI-assisted data enrichment (Gemini API)

---

## 🛠️ Tech Stack

### Backend

- Java
- Spring Boot
- Spring Security
- JPA / Hibernate
- MyBatis (native queries)
- Flyway (database migrations)
- QueryDSL (selectively used, gradually refactored where native queries provide better performance and clarity)
- Domain-Driven Design (aggregate roots, rich domain models, encapsulated business logic

### Data Processing Architecture

The system includes a modular market data ingestion layer.

New data sources can be integrated by defining:

- Source URL
- DTO mapping
- Update strategy
- Schedule via market-updaters in application.yml

This allows new instruments to be added without modifying core business logic.

### Frontend (under active modernization)

- Vite + React
- TypeScript
- Mantine UI with custom styles
- TanStack Router (file-based)
- TanStack Query
- OpenAPI Fetch with generated API clients + TanStack Query integration
- Zustand
- Biome.js (code formatting and linting)

### Database

- PostgreSQL

### DevOps

- Docker
- Dokploy
- GitHub automated builds via Dokploy

---

## 📊 Project Status

This project is under active development.

The initial focus was on delivering core functionality.
As the system matured, structured database migrations and infrastructure improvements were introduced.

New features and architectural refinements are continuously being added.

### Live Demo

A live demo of the platform is available at: [https://borsa.canverse.dev](https://borsa.canverse.dev)

- Demo Account
    - Email: demo2@gmail.com
    - Password: 12345678

### Screenshots

![dashboard.png](docs/screenshots/dashboard.png)
![positions.png](docs/screenshots/positions.png)
![trades.png](docs/screenshots/trades.png)
![portfolio-1.png](docs/screenshots/portfolio-1.png)

---

## 📚 Documentation

### Configuration & Deployment
- [Security Configuration Guide](docs/SECURITY_CONFIGURATION.md) - Environment variables, RSA keys, CORS, and security best practices
- [Upgrade Guide](docs/UPGRADE_GUIDE.md) - Recent changes, migration checklist, and testing procedures

### Architecture & Development
- [Codebase Analysis Summary](docs/CODEBASE_ANALYSIS_SUMMARY.md) - Comprehensive analysis of improvements and architecture
- [API Version Clarification](docs/API_VERSION_CLARIFICATION.md) - API endpoint documentation and versioning strategy

---

## 🔧 Configuration

### Required Environment Variables

For production deployment, configure the following environment variables:

```bash
# Database
export DB_URL="jdbc:postgresql://localhost:5432/stocks"
export DB_USERNAME="postgres"
export DB_PASSWORD="your_password"

# Security - RSA Keys for JWT
export RSA_PRIVATE_KEY="your-rsa-private-key"
export RSA_PUBLIC_KEY="your-rsa-public-key"

# CORS Configuration
export ALLOWED_CORS_ORIGINS="https://your-domain.com"

# Email Domain Restrictions
export ALLOWED_EMAIL_DOMAINS="gmail.com,yahoo.com,custom-domain.com"

# External APIs
export FOREX_RATE_API_KEY="your_forex_api_key"
export GEMINI_API_KEY="your_gemini_api_key"
export GOOGLE_CLIENT_ID="your_google_client_id"
export GOOGLE_CLIENT_SECRET="your_google_client_secret"
```

See [Security Configuration Guide](docs/SECURITY_CONFIGURATION.md) for detailed configuration instructions.