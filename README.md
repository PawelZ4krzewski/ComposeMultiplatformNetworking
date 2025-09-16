## Opis projektu

Projekt demonstruje podejście do tworzenia wieloplatformowej warstwy sieciowej oraz prostego narzędzia benchmarkingowego w oparciu o Compose Multiplatform. Aplikacja umożliwia wykonywanie serii żądań HTTP GET z kontrolą parametrów (adres bazowy, ścieżka, limity czasowe, liczba powtórzeń, warm‑up) oraz prezentuje statystyki (medianę, P95, min, max, liczbę błędów według kategorii). Zestaw predefiniowanych scenariuszy (S1–S6) odzwierciedla typowe przypadki: mały obiekt, lista, błąd 500, timeout, offline oraz echo nagłówków.

## Główne komponenty
- Warstwa UI: Compose Multiplatform (ekrany Bench i Data) – zarządza stanem oraz prezentacją wyników.
- Warstwa logiki: ViewModel dla benchmarków z agregacją wyników i eksportem (CSV/Markdown).
- Warstwa sieciowa: Ktor Client z konfiguracją czasu (HttpTimeout), JSON (kotlinx.serialization), nagłówkami domyślnymi oraz wyłączonym logowaniem podczas pomiarów.
- Scenariusze: Definicje presetów (S1–S6) zawierające bazowy URL, ścieżkę, timeouts oraz opis.
- Model danych: Proste DTO (id, title, body) z tolerancją na brakujące pola (ignoreUnknownKeys).

## Charakterystyka implementacji
- Jeden pomiar czasu na żądanie (monotoniczny znacznik czasu w warstwie sieciowej).
- Brak cache i brak automatycznych retry podczas standardowych pomiarów (retry opcjonalne i domyślnie wyłączone).
- Domyślne nagłówki: User-Agent=NetBench/1.0, Cache-Control=no-cache, Accept=application/json.
- Timeouts parametryzowane: connect / send / receive (domyślnie równoważne, scenariusz timeout ma krótsze wartości).
- Warm-up: możliwość odrzucenia pierwszej próby (stabilizacja środowiska).
- P95 wyliczane jako element o indeksie floor(0.95*(n-1)) po posortowanej liście czasów.
- Błędy klasyfikowane do kategorii: Timeout, NoInternet, Http4xx, Http5xx, Cancel, Unknown; czas błędów również mierzony.

## Scenariusze benchmarku (S1–S6)
- S1 Small – mały obiekt JSON (pojedynczy rekord)
- S2 List – lista ~100 elementów (narzut transferu + dekodowania)
- S3 Error – deterministyczny HTTP 500
- S4 Timeout – opóźniona odpowiedź przekraczająca limit
- S5 Offline – brak połączenia (np. tryb samolotowy)
- S6 Headers – echo nagłówków w celu potwierdzenia konfiguracji klienta

## Agregacja wyników
Dla każdej serii obliczane są: liczba pomiarów, median, P95, min, max oraz liczby błędów w podziale na kategorie. Dane eksportowalne są jako CSV oraz tabela Markdown. Każda próba logowana jest pojedynczą linią z czasem.

## Konfiguracja i uruchomienie
Parametry można nadpisać przez właściwości Gradle:
```
./gradlew :composeApp:assembleDebug \
  -PBASE_URL=https://dummyjson.com \
  -PCONNECT_TIMEOUT_MS=8000 \
  -PSEND_TIMEOUT_MS=8000 \
  -PRECEIVE_TIMEOUT_MS=8000 \
  -PENABLE_RETRY=false
```
Instalacja na Androidzie (debug):
```
./gradlew :composeApp:installDebug
```
iOS (framework):
```
./gradlew :composeApp:syncFramework
```
Następnie dołączyć wygenerowany framework w hostującej aplikacji Xcode i uruchomić na symulatorze.

## Użyte biblioteki / technologie
- Compose Multiplatform (UI)
- Ktor Client (HTTP + plugins: HttpTimeout, ContentNegotiation, Logging wyłączony w pomiarach)
- kotlinx.serialization (dekodowanie JSON)
- Coroutines (współbieżność, zawieszenia)
- Gradle BuildConfig (parametry runtime)
