# Upgrade Guide

## Recent Changes

This document outlines recent upgrades and improvements to the Borsa application.

## Version: Current

### Security Improvements

#### 1. Externalized RSA Key Configuration
**Status**: ✅ Completed

**What Changed**:
- RSA private and public keys can now be provided via environment variables
- Removed hardcoded TODO comment about moving keys to environment

**Action Required**:
- For production deployments, set `RSA_PRIVATE_KEY` and `RSA_PUBLIC_KEY` environment variables
- See [Security Configuration Guide](./SECURITY_CONFIGURATION.md) for details

**Backward Compatibility**:
- Development environments continue to use classpath keys as fallback
- No breaking changes

#### 2. Configurable CORS Origins
**Status**: ✅ Completed

**What Changed**:
- CORS allowed origins are now configurable via `ALLOWED_CORS_ORIGINS` environment variable
- Removed hardcoded origins from `SecurityConfiguration.java`

**Action Required**:
- Review and set `ALLOWED_CORS_ORIGINS` to match your deployment domains
- Default includes `http://localhost:5173/` and `https://borsa.canverse.dev`

**Backward Compatibility**:
- Defaults match previous hardcoded values
- No breaking changes

#### 3. Configurable Email Domain Restrictions
**Status**: ✅ Completed

**What Changed**:
- Allowed email domains for registration are now configurable
- Previously hardcoded array in `UserService.java` replaced with configuration property

**Action Required**:
- Optionally set `ALLOWED_EMAIL_DOMAINS` to customize allowed domains
- Default includes: gmail.com, yahoo.com, hotmail.com, outlook.com, icloud.com

**Backward Compatibility**:
- Defaults match previous hardcoded values
- No breaking changes

### Database Migration Improvements

#### 4. Flyway Enabled by Default
**Status**: ✅ Completed

**What Changed**:
- Flyway is now enabled by default (previously disabled in development)
- Can be disabled via `FLYWAY_ENABLED=false` if needed

**Action Required**:
- Ensure your database is at the correct baseline version
- Review Flyway migration scripts in `src/main/resources/db/migrations`

**Backward Compatibility**:
- Development databases may need to run migrations on first startup
- Production deployments were already using Flyway

### Project Metadata

#### 5. Complete POM.xml Metadata
**Status**: ✅ Completed

**What Changed**:
- Added license information (MIT License)
- Added developer information
- Added SCM (Source Control Management) details
- Added project URL

**Action Required**:
- No action required

**Benefits**:
- Better Maven repository compatibility
- Improved project documentation
- Clearer licensing information

## Configuration Migration Checklist

For existing deployments, verify the following:

- [ ] RSA keys are provided via environment variables (production)
- [ ] CORS origins include all your deployment domains
- [ ] Email domains match your user requirements
- [ ] Flyway migrations have been applied successfully
- [ ] All environment variables are documented in your deployment scripts

## New Configuration Properties

```yaml
# Application Security Configuration
app:
  security:
    allowed-origins: ${ALLOWED_CORS_ORIGINS:http://localhost:5173,https://borsa.canverse.dev}
    allowed-email-domains: ${ALLOWED_EMAIL_DOMAINS:gmail.com,yahoo.com,hotmail.com,outlook.com,icloud.com}

# RSA Key Configuration
rsa:
  private-key: ${RSA_PRIVATE_KEY:classpath:certs/private.pem}
  public-key: ${RSA_PUBLIC_KEY:classpath:certs/public.pem}

# Flyway Configuration
spring:
  flyway:
    enabled: ${FLYWAY_ENABLED:true}
```

## Testing Your Configuration

### Verify RSA Keys
```bash
# Check if JWT tokens are being generated correctly
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"email":"demo2@gmail.com","password":"12345678"}'
```

### Verify CORS
```bash
# Check if CORS headers are set correctly
curl -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -X OPTIONS http://localhost:8080/api/auth/token -v
```

### Verify Email Domains
```bash
# Try registering with an allowed domain
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@gmail.com","password":"password123","name":"Test User"}'

# Try registering with a disallowed domain (should fail)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"Test User"}'
```

## Rollback Instructions

If you need to rollback these changes:

1. **RSA Keys**: Set environment variables to empty to force classpath usage
2. **CORS**: Revert `SecurityConfiguration.java` to use hardcoded List.of()
3. **Email Domains**: Revert `UserService.java` to use static array
4. **Flyway**: Set `FLYWAY_ENABLED=false`

## Future Improvements

### Planned
- API versioning consolidation (TradeController v1/v2)
- Enhanced JWT token refresh mechanism
- Role-based access control improvements
- Additional security headers configuration

### Under Consideration
- OAuth2 provider support (GitHub, Microsoft)
- Two-factor authentication
- Rate limiting configuration
- API key management system

## Support

For issues or questions:
- GitHub Issues: https://github.com/candogusyilmaz/borsa/issues
- Documentation: https://github.com/candogusyilmaz/borsa/tree/main/docs
