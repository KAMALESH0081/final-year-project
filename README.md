# Traffic Sign Detection Android App

## Overview

A real-time Android traffic sign detection application powered by YOLOv8 and TensorFlow Lite (FP16). The application performs on-device inference using the smartphone camera and provides configurable detection settings for improved usability and deployment flexibility.

The project covers the complete computer vision pipeline, including dataset collection, annotation, model training, model optimization, TensorFlow Lite conversion, and Android deployment.

## Features

### Real-Time Detection

* Live traffic sign detection using the device camera
* On-device inference using TensorFlow Lite
* Low-latency real-time performance

### Detection Controls

* Global confidence threshold adjustment
* Class-specific confidence threshold adjustment
* IoU threshold configuration
* Enable or disable individual traffic sign classes

### Alert Management

* Detection history logging
* Timestamped detection records
* Confidence score tracking
* Alert saving for future review

### Cooldown Mechanism

* Configurable cooldown period for detected classes
* Prevents repeated alerts for the same traffic sign
* Improves user experience during continuous detection

## Model

* Architecture: YOLOv8n
* Deployment Format: TensorFlow Lite FP16
* Model Size Reduction:

  * Original Model: ~6 MB
  * TFLite FP16 Model: ~3 MB

## Technology Stack

* Python
* YOLOv8
* TensorFlow Lite
* OpenCV
* Android Studio
* Java/Kotlin
* CameraX

## Application Workflow

Road Scene → Camera Feed → TFLite Model → Traffic Sign Detection → Alert Generation → Detection History Storage

## Screenshots

### Detection Screen

![Detection Screen](screenshots/final_year_project_img_1.jpg)

### Settings Panel

![Settings Screen](screenshots/final_year_project_img_2.jpg)

## Key Functionalities

* Real-time traffic sign recognition
* Mobile edge AI deployment
* Dynamic confidence threshold tuning
* Per-class detection control
* Detection history management
* Alert cooldown handling
* Lightweight FP16 optimized model

## Future Improvements

* Support additional traffic sign categories
* GPS-based location logging
* Voice alerts for detected signs
* Cloud synchronization of detection logs

## Author

Kamalesh V
