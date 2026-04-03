# Release Notes

## Version 1.3.0 (2026-02-27)

### 🎉 Major Features

#### Cross-Platform Support
- **PowerShell Scripts**: Added Windows-compatible PowerShell versions of all bash scripts
  - `scripts/wait-for-stack-ready.ps1` - Stack readiness checker
  - `scripts/init-postgres-dbs.ps1` - Manual database initialization
- **Line Ending Management**: Added `.gitattributes` for automatic cross-platform line ending handling
- **Windows Installation Guide**: Complete Java/Maven/Gradle installation instructions using WinGet, Scoop, and Chocolatey

#### Monitoring & Debugging Tools
- **Kafka UI**: Added web-based Kafka monitoring interface (Port 8090)
  - Real-time message browsing
  - Topic management
  - Consumer group monitoring
  - Broker health checks
- **pgAdmin**: Added PostgreSQL database management tool (Port 5050)
  - Web-based database administration
  - Query execution
  - Schema visualization
  - Multi-database support

#### Enhanced Documentation
- **Architecture Diagrams**: Comprehensive Mermaid diagrams added
  - High-level system architecture
  - Microservices communication patterns
  - Complete Saga flow sequences
  - Compensation flow diagrams
  - Order status state machine
  - Kafka topics and event flow
- **Code Architecture Documentation**: Detailed design patterns analysis
  - Layered architecture overview
  - Design patterns identification (not MVC/MVP)
  - Package structure patterns
  - Request flow patterns
  - Dependency injection patterns
- **Scripts Documentation**: Complete guide in `scripts/README.md`
  - Cross-platform usage instructions
  - Troubleshooting guides
  - Best practices

#### Frontend Improvements
- **Enhanced Inventory Display**: Added Reserved and Total columns
  - Clear visibility of available vs reserved inventory
  - Better understanding of inventory reservation
  - Improved debugging capabilities

### 🐛 Bug Fixes

- Fixed YAML formatting issues in `podman-compose.yml`
- Fixed Kafka service network configuration
- Resolved Windows path issues with pgAdmin configuration
- Fixed inventory display showing reserved quantity instead of available

### 📚 Documentation Updates

- Updated README with comprehensive architecture documentation
- Added Windows-specific setup instructions
- Documented all monitoring tools (Kafka UI, pgAdmin)
- Added troubleshooting guides for common issues
- Created detailed scripts documentation

### 🔧 Technical Improvements

- Improved error handling in PowerShell scripts
- Added compatibility for PowerShell 5.1+ (Windows PowerShell)
- Simplified pgAdmin configuration for better cross-platform support
- Enhanced .gitignore for sensitive files

### 📦 New Files

- `scripts/wait-for-stack-ready.ps1` - PowerShell stack readiness checker
- `scripts/init-postgres-dbs.ps1` - PowerShell database initialization
- `scripts/README.md` - Complete scripts documentation
- `pgadmin-servers.json` - pgAdmin server configuration template
- `.gitattributes` - Cross-platform line ending configuration
- `RELEASE_NOTES.md` - This file

### 🔄 Breaking Changes

None. This release is fully backward compatible with version 1.2.0.

### 📋 Migration Guide

No migration needed. Simply pull the latest changes and restart your containers:

```bash
git pull
podman-compose -f podman-compose.yml down
podman-compose -f podman-compose.yml up -d
```

For Windows users, you can now use PowerShell scripts:
```powershell
.\scripts\init-postgres-dbs.ps1
.\scripts\wait-for-stack-ready.ps1
```

### 🎯 Highlights

**For Windows Users:**
- Full PowerShell support
- Automatic line ending handling
- Complete installation guides
- No more bash script issues

**For All Users:**
- Kafka UI for real-time event monitoring
- pgAdmin for database management
- Enhanced inventory visibility
- Comprehensive architecture documentation

**For Developers:**
- Clear design patterns documentation
- Detailed architecture diagrams
- Better debugging tools
- Improved troubleshooting guides

### 📊 Statistics

- **New Scripts**: 2 PowerShell scripts
- **New Tools**: 2 monitoring tools (Kafka UI, pgAdmin)
- **Documentation**: 500+ lines of new documentation
- **Diagrams**: 10+ new Mermaid diagrams
- **Commits**: 15+ commits since v1.2.0

### 🙏 Acknowledgments

Special thanks to all contributors who helped test and improve cross-platform compatibility!

### 🔗 Links

- [GitHub Repository](https://github.com/hacisimsek/ecommerce-microservices)
- [Documentation](README.md)
- [Scripts Guide](scripts/README.md)

---

## Version 1.2.0 (Previous Release)

See previous release notes for version 1.2.0 changes.

---

**Full Changelog**: v1.2.0...v1.3.0