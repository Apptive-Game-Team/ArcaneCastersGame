# Localization Implementation

This document describes the localization implementation for error messages in the WordOnlineServer.

## Overview

The server now supports localized error messages in Korean (ko) and English (en). The localization is based on the HTTP `Accept-Language` header sent by clients.

## How It Works

1. **Locale Resolution**: The `AcceptHeaderLocaleResolver` reads the `Accept-Language` header from incoming HTTP requests
2. **Message Retrieval**: The `LocalizationService` retrieves messages from resource bundles based on the resolved locale
3. **Default Locale**: If no `Accept-Language` header is provided, Korean (ko) is used as the default

## Client Usage

To receive error messages in a specific language, clients should include the `Accept-Language` header in their requests:

### For English:
```
Accept-Language: en
```

### For Korean:
```
Accept-Language: ko
```

### Example with curl:
```bash
# English
curl -H "Accept-Language: en" http://127.0.0.1:6515/api/endpoint

# Korean  
curl -H "Accept-Language: ko" http://127.0.0.1:6515/api/endpoint
```

## Supported Error Messages

The following error messages are currently localized:

| Message Key | Korean | English |
|------------|--------|---------|
| error.invalid.request | 요청이 잘못됐습니다. | Invalid request. |
| error.unauthorized | 인증되지 않았습니다. | Unauthorized. |
| error.not.authorized | 권한이 없습니다. | Not authorized. |
| error.deck.not.found | 덱을 찾을 수 없습니다. | Deck not found. |
| error.deck.illegal | 잘못된 덱입니다. | Illegal deck. |
| error.member.not.found | 회원을 찾을 수 없습니다. | Member not found. |
| error.user.not.found | 사용자를 찾을 수 없습니다: {0} | User not found: {0} |
| error.token.invalid | 유효하지 않은 토큰입니다. | Invalid token. |
| error.authorization.denied | 접근이 거부되었습니다. | Authorization denied. |
| error.register.failed | 등록할 수 없습니다. | Can't register. |
| error.internal | 서버 내부 오류가 발생했습니다. | Internal server error. |

## Adding New Messages

To add new localized messages:

1. Add the message key and text to `src/main/resources/messages.properties` (default/Korean)
2. Add the message key and text to `src/main/resources/messages_ko.properties` (Korean)
3. Add the message key and text to `src/main/resources/messages_en.properties` (English)
4. Use `localizationService.getMessage("message.key")` in your code

### Example:

**messages.properties**:
```properties
error.new.error=새로운 에러 메시지입니다.
```

**messages_en.properties**:
```properties
error.new.error=This is a new error message.
```

**Code**:
```java
throw new IllegalArgumentException(localizationService.getMessage("error.new.error"));
```

## Configuration

The localization is configured in `LocalizationConfig.java`:

```java
@Bean
public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
    localeResolver.setDefaultLocale(Locale.KOREAN);
    return localeResolver;
}

@Bean
public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(true);
    return messageSource;
}
```

## Testing

Tests for the localization functionality can be found in `LocalizationConfigTest.java`. The tests verify:
- Korean message retrieval
- English message retrieval
- Messages with parameters
- Default locale behavior
- LocalizationService functionality
