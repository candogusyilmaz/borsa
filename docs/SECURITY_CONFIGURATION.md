# Security Configuration Guide

## Overview
This document outlines the security configuration best practices and environment variable setup for the Borsa application.

## RSA Key Configuration

### Production Deployment
For production environments, **DO NOT** use the RSA keys from the classpath. Instead, provide them as environment variables:

```bash
export RSA_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----
Your private key content here
-----END PRIVATE KEY-----"

export RSA_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----
Your public key content here
-----END PUBLIC KEY-----"
```

### Key Generation
To generate new RSA key pairs:

```bash
# Generate private key
openssl genrsa -out private.pem 2048

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem
```

### Default Behavior
- In development: Uses `classpath:certs/private.pem` and `classpath:certs/public.pem` if environment variables are not set
- In production: **Must** set `RSA_PRIVATE_KEY` and `RSA_PUBLIC_KEY` environment variables

## CORS Configuration

### Allowed Origins
Configure allowed CORS origins using the `ALLOWED_CORS_ORIGINS` environment variable:

```bash
export ALLOWED_CORS_ORIGINS="http://localhost:5173,https://borsa.canverse.dev,https://your-custom-domain.com"
```

**Default**: `http://localhost:5173,https://borsa.canverse.dev`

## Email Domain Restrictions

### Allowed Email Domains
Configure which email domains are allowed for user registration:

```bash
export ALLOWED_EMAIL_DOMAINS="gmail.com,yahoo.com,hotmail.com,outlook.com,icloud.com,custom-domain.com"
```

**Default**: `gmail.com,yahoo.com,hotmail.com,outlook.com,icloud.com`

## Flyway Configuration

### Database Migration
Flyway is now enabled by default. To disable it (not recommended):

```bash
export FLYWAY_ENABLED=false
```

**Default**: `true`

## Complete Environment Variable Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `RSA_PRIVATE_KEY` | RSA private key for JWT signing | `classpath:certs/private.pem` |
| `RSA_PUBLIC_KEY` | RSA public key for JWT verification | `classpath:certs/public.pem` |
| `ALLOWED_CORS_ORIGINS` | Comma-separated list of allowed CORS origins | `http://localhost:5173,https://borsa.canverse.dev` |
| `ALLOWED_EMAIL_DOMAINS` | Comma-separated list of allowed email domains | `gmail.com,yahoo.com,hotmail.com,outlook.com,icloud.com` |
| `FLYWAY_ENABLED` | Enable/disable Flyway migrations | `true` |

## Security Best Practices

1. **Never commit private keys to version control**
2. **Use environment variables or secrets management systems** (AWS Secrets Manager, HashiCorp Vault, etc.) in production
3. **Rotate RSA keys periodically**
4. **Limit CORS origins** to only trusted domains
5. **Review allowed email domains** based on your user base requirements
6. **Keep Flyway enabled** to ensure database schema consistency
7. **Use HTTPS** for all production deployments
8. **Enable security headers** in your reverse proxy/load balancer

## Docker Deployment Example

```dockerfile
ENV RSA_PRIVATE_KEY="your-private-key"
ENV RSA_PUBLIC_KEY="your-public-key"
ENV ALLOWED_CORS_ORIGINS="https://your-domain.com"
ENV ALLOWED_EMAIL_DOMAINS="your-domain.com,gmail.com"
```

Or use Docker secrets for sensitive data:

```bash
docker run -e RSA_PRIVATE_KEY_FILE=/run/secrets/rsa_private_key \
           -e RSA_PUBLIC_KEY_FILE=/run/secrets/rsa_public_key \
           your-image
```
