#include <Arduino.h>
#include "BluetoothSerial.h"

// 🔹 Bluetooth Serial object
BluetoothSerial SerialBT;

// 🔹 Pin Definitions for Solenoid Actuators
int actuatorPins[6] = {2, 4, 5, 18, 19, 21}; // Dots 1–6

// 🔹 Global State Variables
String messageToDisplay = "";
int currentCharIndex = 0;
unsigned long lastCharDisplayTime = 0;
const long displayInterval = 2000; // Time to display one character (ms)
const long spaceInterval = 500;    // Time for the space between characters (ms)
bool isDisplayingChar = false;     // Tracks if a character is currently raised

// 🔹 Forward declarations
byte encodeToBraille(char ch);
byte encodeDigit(char ch);
void displayBrailleChar();
void clearDots();

void setup() {
  Serial.begin(115200);
  Serial.println("✅ Initializing BrailleLink ESP32...");

  // Setup actuator pins
  for (int i = 0; i < 6; i++) {
    pinMode(actuatorPins[i], OUTPUT);
    digitalWrite(actuatorPins[i], LOW);
  }
  Serial.println("Actuators configured.");

  // --- Classic Bluetooth Serial Setup ---
  SerialBT.begin("BrailleLink_ESP32"); // Device name
  Serial.println("Bluetooth started. Pair and connect to 'BrailleLink_ESP32'.");
}

void loop() {
  // Check for incoming Bluetooth messages
  if (SerialBT.available()) {
    messageToDisplay = SerialBT.readString(); // Read incoming message
    currentCharIndex = 0;
    lastCharDisplayTime = 0;
    isDisplayingChar = false;
    Serial.print("Received message: ");
    Serial.println(messageToDisplay);
  }

  // Non-blocking display logic
  if (currentCharIndex < messageToDisplay.length()) {
    unsigned long currentTime = millis();

    if (isDisplayingChar) {
      if (currentTime - lastCharDisplayTime > displayInterval) {
        clearDots();
        isDisplayingChar = false;
        lastCharDisplayTime = currentTime; // Timer for space
      }
    } else {
      if (currentTime - lastCharDisplayTime > spaceInterval) {
        displayBrailleChar();
        isDisplayingChar = true;
        lastCharDisplayTime = currentTime;
        currentCharIndex++;
      }
    }
  }
}

void displayBrailleChar() {
  static bool numberMode = false;
  char ch = messageToDisplay.charAt(currentCharIndex);
  byte pattern = 0b000000;

  if (ch == '#') {
    numberMode = true;
    pattern = 0b001111; // Number sign
  } else if (isspace(ch)) {
    numberMode = false;
    pattern = 0b000000; // Space
  } else {
    if (numberMode && isdigit(ch)) {
      pattern = encodeDigit(ch);
    } else {
      pattern = encodeToBraille(ch);
    }
  }

  Serial.print("Displaying '");
  Serial.print(ch);
  Serial.print("': 0b");
  Serial.println(pattern, BIN);

  for (int i = 0; i < 6; i++) {
    bool isRaised = (pattern >> (5 - i)) & 0x01;
    digitalWrite(actuatorPins[i], isRaised ? HIGH : LOW);
  }
}

void clearDots() {
  Serial.println("...clearing dots.");
  for (int i = 0; i < 6; i++) {
    digitalWrite(actuatorPins[i], LOW);
  }
}

// --- Braille Encoding Functions ---

byte encodeToBraille(char ch) {
  ch = tolower(ch);
  switch (ch) {
    case 'a': return 0b100000; case 'b': return 0b110000; case 'c': return 0b100100;
    case 'd': return 0b100110; case 'e': return 0b100010; case 'f': return 0b110100;
    case 'g': return 0b110110; case 'h': return 0b110010; case 'i': return 0b010100;
    case 'j': return 0b010110; case 'k': return 0b101000; case 'l': return 0b111000;
    case 'm': return 0b101100; case 'n': return 0b101110; case 'o': return 0b101010;
    case 'p': return 0b111100; case 'q': return 0b111110; case 'r': return 0b111010;
    case 's': return 0b011100; case 't': return 0b011110; case 'u': return 0b101001;
    case 'v': return 0b111001; case 'w': return 0b010111; case 'x': return 0b101101;
    case 'y': return 0b101111; case 'z': return 0b101011; default: return 0b000000;
  }
}

byte encodeDigit(char ch) {
  switch (ch) {
    case '1': return 0b100000; case '2': return 0b110000; case '3': return 0b100100;
    case '4': return 0b100110; case '5': return 0b100010; case '6': return 0b110100;
    case '7': return 0b110110; case '8': return 0b110010; case '9': return 0b010100;
    case '0': return 0b010110; default: return 0b000000;
  }
}
