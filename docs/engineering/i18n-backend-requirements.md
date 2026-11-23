# i18n hesap tercihleri için backend gereksinimleri

Web uygulaması arayüz dilini (`en` / `tr`), ülkeyi (ISO 3166-1 alpha-2) ve gösterim formatını ayrı ele alır. Dil Lingui katalogunu, HTML dilini ve takvim etiketlerini belirler. Ülke sayı ve tarih biçimini belirleyebilir. Varsayılanlar `en-US` ve `tr-TR`. Örneğin İngilizce arayüz kullanan bir Türkiye hesabı `tr-TR` gösterim biçimini kullanabilir.

## Mevcut sözleşme

| Akış | Mevcut kod | Önerilen ek |
| --- | --- | --- |
| Hesap kaydı | `TokenController.register` → `UserService.register` / `UserRegistrationRequest` | İsteğe bağlı `language`, `countryCode` ve `preferredCurrencyCode` |
| Onboarding | `OnboardingController.completeOnboarding` → `UserService.completeOnboarding` / `OnboardingRequest` | İlk hesap tercihlerini aynı işlem içinde kaydetme |
| Profil okuma | `AccountController.getMyAccount` → `UserService.getMe` / `UserView.from` | `language`, `countryCode` ve `preferredCurrencyCode` alanlarını döndürme |
| Tercih güncelleme | `AccountController` | `PATCH /api/account/preferences` → `UserService.updatePreferences` |

`User` varlığı `account.users` tablosunda. `Country` varlığı `public.countries` tablosunu kullanıyor. `isoCode` alanı üç karaktere kadar saklayabiliyor ve ülke verisi `TR`, `GB`, `US` gibi iki harfli kodlar içeriyor. `Currency` varlığının `public.currencies.code` alanı benzersiz ve en fazla 10 karakter.

Login, Google login, kayıt ve token yenileme `TokenCreateResponse` döndürüyor. `UserView` bu token DTO’sundan ayrı ve mevcut web OpenAPI çıktısında `GET /api/account/me` bulunmuyor.

## Flyway ve JPA

Mevcut son geçiş `V14__crypto_instruments.sql`. Önerilen sonraki geçiş `server/src/main/resources/db/migrations/V15__user_locale_preferences.sql`. Geçiş yazılırken sıradaki boş Flyway sürümünü tekrar kontrol edin.

```sql
ALTER TABLE account.users
    ADD COLUMN language VARCHAR(2),
    ADD COLUMN country_id BIGINT,
    ADD COLUMN preferred_currency_code VARCHAR(10),
    ADD CONSTRAINT ck_users_language CHECK (language IN ('en', 'tr')),
    ADD CONSTRAINT fk_users_country
        FOREIGN KEY (country_id) REFERENCES public.countries (id),
    ADD CONSTRAINT fk_users_preferred_currency
        FOREIGN KEY (preferred_currency_code) REFERENCES public.currencies (code);
```

- Mevcut hesaplar için sütunları boş bırakın. Henüz tercih seçmemiş cihazlar kendi dil çözümlemesini kullanmaya devam eder.
- `User` üzerinde dili küçük harfe normalize edin ve yalnızca `en` ile `tr` kabul edin. Ülkeyi `@ManyToOne(fetch = LAZY)` ile `Country` üzerinden bağlayın. API’de sayısal kimlik yerine `countryCode` verin.
- Gelen ülke kodunu büyük harfe normalize edin ve `Country.isoCode` üzerinden arayın. Bu sorgu için bir `CountryRepository.findByIsoCode` eklenebilir. Tercih para birimi verildiyse `CurrencyRepository.findByCode` ile aktif para birimini doğrulayın.
- `UserService.getMe` bugün `findById(...).map(UserView::from)` kullanır. Yeni ilişkiler eklenince DTO’yu okuma işlemi içinde oluşturun veya ilgili ilişkileri profil sorgusunda birlikte yükleyin.
- `preferredCurrencyCode` yeni işlemlerde veya panellerde kullanılabilecek bir kullanıcı varsayılanıdır. BTC, TRY veya USD gibi mevcut varlık ve portföy değerleri kendi para biriminde kalır.

## Kayıt ve onboarding

`UserRegistrationRequest` kaydına isteğe bağlı `String language`, `String countryCode` ve opsiyonel para birimi tercihi ekleyin. `UserService.register` doğrulama ve kullanıcı oluşturma işleminde bu alanları uygulasın. `TokenController.googleLogin` bugün yeni Google hesapları için aynı request kaydının kurucusunu çağırıyor. Bu çağrıya yeni isteğe bağlı alanlar için `null` geçin veya mevcut üç parametreli kurucuyu koruyun. Google ile açılan hesaplar eksik tercihleri onboarding sırasında tamamlayabilir.

```json
{
  "name": "Deniz Örnek",
  "email": "deniz@example.com",
  "password": "example-password",
  "language": "tr",
  "countryCode": "TR"
}
```

`OnboardingRequest` aynı isteğe bağlı alanları kabul etsin. `UserService.completeOnboarding` mevcut portföy ve panel kaydıyla aynı transaction içinde tercihleri uygulasın. Gönderilmeyen tercihler kayıt sırasında seçilen değerleri korusun. Mevcut `currencyCode` alanı ilk gösterge panelinin raporlama para birimi olarak kalsın.

```json
{
  "portfolio": { "portfolioName": "Uzun Vadeli", "color": "#3b82f6" },
  "dashboardName": "Yatırımlarım",
  "currencyCode": "TRY",
  "language": "en",
  "countryCode": "TR",
  "preferredCurrencyCode": "GBP"
}
```

Bu örnekte panel TRY cinsinden, arayüz İngilizce, gösterim biçimi Türkiye’ye göre ve hesap için önerilen para birimi GBP olur.

## Profil ve tercih endpoint'i

`UserView` ve `UserView.from(User)` üç tercihi nullable string olarak döndürsün. `GET /api/account/me` ile `PATCH /api/account/preferences` aynı DTO’yu kullansın.

- `PATCH /api/account/preferences` oturumdaki kullanıcıya uygulanır. Kullanıcı kimliği mevcut `AuthenticationProvider` üzerinden alınır. `UserService.updatePreferences`, doğrulama ve kaydı tek bir transaction içinde yürütür.
- Gönderilmeyen alanlar korunur. `countryCode: null` ve `preferredCurrencyCode: null` ilgili tercihi temizler. `language` gönderiliyorsa `en` veya `tr` olmalıdır. PATCH DTO’su alanın eksik olmasıyla açıkça null verilmesini ayırt etmelidir.
- Başarıda `200 OK` ve güncel `UserView` dönün. Desteklenmeyen dil, bilinmeyen ülke, etkin olmayan veya bilinmeyen para birimi için alanı belirten `400 Bad Request` dönün. Oturum yoksa mevcut Spring Security davranışı geçerli olsun.
- `Accept-Language` değerini istek bazlı yanıt dili olarak kullanın. Başlık tek başına kalıcı hesap tercihini değiştirmesin.

```json
{
  "language": "tr",
  "countryCode": "GB"
}
```

```json
{
  "id": 42,
  "name": "Deniz Örnek",
  "email": "deniz@example.com",
  "lastLoginAt": "2026-11-23T12:00:00Z",
  "createdAt": "2026-07-18T09:00:00Z",
  "onboardingCompleted": true,
  "permissions": [],
  "language": "tr",
  "countryCode": "GB",
  "preferredCurrencyCode": "GBP"
}
```

## Web bağlantısı

1. Spring OpenAPI belgesinde profil okuma ve güncelleme endpoint’lerini doğrulayın. `web/` altında `npm run generate:openapi` ile tipleri yeniden üretin.
2. `web/src/shared/i18n/preferences-adapter.ts` dosyasındaki **`LocaleProfile` TODO** için geçici token sınır tipini üretilen `components['schemas']['UserView']` ile değiştirin.
3. Aynı dosyadaki **`syncLocaleFromUser` TODO** için `AppProviders` içindeki `UserLocaleProvider` üzerinden oturum açıldığında veya yenilendiğinde `GET /api/account/me` profilini geçin. Profil sorgusu hesapla anahtarlanmalı ve çıkışta temizlenmeli. Adaptör etkin seçimi, `canverse:locale` önbelleğini, profil dilini ve tarayıcı dilini bu sırayla değerlendirir. Profil ülkesini bundan bağımsız uygular.
4. **`saveUserLocalePreference` TODO** içinde OpenAPI istemcisiyle `PATCH /api/account/preferences` çağrısını yapın. Dil seçicisi yalnızca `language` gönderir. Ülke düzenleme ekranı eklendiğinde o alanı, açıkça değiştirildiyse, aynı sözleşmeyle gönderin. Hızlı dil değişimlerinde yazımları sıraya alın ve en güncel seçimi profil önbelleğine yansıtın. Ağ hatası mevcut cihazdaki seçimi geri almamalı.
5. Kayıt formunda etkin dili ve kullanıcının varsa ülke seçimini `UserRegistrationRequest` ile gönderin. Onboarding tamamlanınca profil sorgusunu yenileyin. Ülkeyi tarayıcı dilinden tahmin edip hesaba kaydetmeyin.
