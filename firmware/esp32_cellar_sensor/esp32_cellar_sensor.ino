/*
  Wine Cellar climate sensor for ESP32
  ------------------------------------
  Reads temperature + humidity every 10 minutes and writes it straight to the app's
  Firestore database, so every phone sees the same live reading, history and alerts.

  Setup:
    1. Arduino IDE: install the "esp32" boards package (Espressif) and, from Library Manager,
       "Adafruit SHT31 Library" (for an SHT31/SHT30) or "DHT sensor library" (for a DHT22).
    2. Fill in WIFI_SSID / WIFI_PASSWORD below.
    3. In the app: Settings → Temperature sensor. Copy CELLAR_ID and SENSOR_KEY into this file.
    4. Upload, open Serial Monitor at 115200 baud and check for "HTTP 200".

  Wiring (SHT31, I2C):  VIN→3V3, GND→GND, SDA→GPIO21, SCL→GPIO22
  Wiring (DHT22):       VCC→3V3, GND→GND, DATA→GPIO4 (10k pull-up to 3V3 if your board lacks one)

  If you get "HTTP 403" mentioning the API key or Android apps: the Firebase API key is
  restricted to Android. In Google Cloud console → APIs & Services → Credentials, create a new
  API key restricted to "Cloud Firestore API" only and paste it into FIREBASE_API_KEY.
*/

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <time.h>

// ---- choose ONE sensor ----
#define SENSOR_SHT31
// #define SENSOR_DHT22

#ifdef SENSOR_SHT31
  #include <Wire.h>
  #include <Adafruit_SHT31.h>
  Adafruit_SHT31 sht31 = Adafruit_SHT31();
#endif
#ifdef SENSOR_DHT22
  #include <DHT.h>
  #define DHT_PIN 4
  DHT dht(DHT_PIN, DHT22);
#endif

// ---- your settings ----
const char* WIFI_SSID       = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD   = "YOUR_WIFI_PASSWORD";
const char* CELLAR_ID       = "PASTE_CELLAR_ID_FROM_APP";
const char* SENSOR_KEY      = "PASTE_SENSOR_KEY_FROM_APP";

// From app/google-services.json (already public in the repo)
const char* FIREBASE_PROJECT_ID = "wine-cellar-app-c1107";
const char* FIREBASE_API_KEY    = "AIzaSyATKV0t7n2uJCDZaq0SjRYATqz8XHLP17c";

const unsigned long READ_INTERVAL_MS = 10UL * 60UL * 1000UL;   // every 10 minutes

// Optional calibration offsets if your sensor reads a little high/low
const float TEMP_OFFSET_C = 0.0;
const float HUMIDITY_OFFSET = 0.0;

unsigned long lastSend = 0;

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("Connecting to %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) {
    delay(500);
    Serial.print(".");
  }
  Serial.println(WiFi.status() == WL_CONNECTED ? " connected" : " failed (will retry)");
}

bool syncClock() {
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  struct tm t;
  for (int i = 0; i < 20; i++) {
    if (getLocalTime(&t, 500) && t.tm_year > 120) return true;
  }
  return false;
}

bool readSensor(float &tempC, float &humidity) {
#ifdef SENSOR_SHT31
  tempC = sht31.readTemperature();
  humidity = sht31.readHumidity();
#endif
#ifdef SENSOR_DHT22
  tempC = dht.readTemperature();
  humidity = dht.readHumidity();
#endif
  if (isnan(tempC) || isnan(humidity)) return false;
  tempC += TEMP_OFFSET_C;
  humidity += HUMIDITY_OFFSET;
  return true;
}

String isoTimestampUtc() {
  time_t now = time(nullptr);
  struct tm t;
  gmtime_r(&now, &t);
  char buf[25];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &t);
  return String(buf);
}

bool sendReading(float tempC, float humidity) {
  WiFiClientSecure client;
  client.setInsecure();   // skips certificate checks; fine for a hobby sensor
  HTTPClient http;

  String url = String("https://firestore.googleapis.com/v1/projects/") + FIREBASE_PROJECT_ID +
               "/databases/(default)/documents/cellars/" + CELLAR_ID + "/climate?key=" + FIREBASE_API_KEY;

  String body = String("{\"fields\":{") +
    "\"temperature\":{\"doubleValue\":" + String(tempC, 2) + "}," +
    "\"humidity\":{\"doubleValue\":" + String(humidity, 1) + "}," +
    "\"ts\":{\"timestampValue\":\"" + isoTimestampUtc() + "\"}," +
    "\"sensorKey\":{\"stringValue\":\"" + SENSOR_KEY + "\"}" +
    "}}";

  if (!http.begin(client, url)) {
    Serial.println("HTTP begin failed");
    return false;
  }
  http.addHeader("Content-Type", "application/json");
  int code = http.POST(body);
  Serial.printf("HTTP %d\n", code);
  if (code != 200) Serial.println(http.getString());
  http.end();
  return code == 200;
}

void takeAndSend() {
  float t, h;
  if (!readSensor(t, h)) {
    Serial.println("Sensor read failed - check wiring");
    return;
  }
  Serial.printf("%.2f C  %.1f %%RH\n", t, h);
  connectWifi();
  if (WiFi.status() == WL_CONNECTED) sendReading(t, h);
}

void setup() {
  Serial.begin(115200);
  delay(200);
#ifdef SENSOR_SHT31
  Wire.begin();
  if (!sht31.begin(0x44)) Serial.println("SHT31 not found at 0x44 (try 0x45)");
#endif
#ifdef SENSOR_DHT22
  dht.begin();
#endif
  connectWifi();
  if (!syncClock()) Serial.println("Clock sync failed - readings need the correct time");
  takeAndSend();
  lastSend = millis();
}

void loop() {
  if (millis() - lastSend >= READ_INTERVAL_MS) {
    lastSend = millis();
    takeAndSend();
  }
  delay(1000);
}
