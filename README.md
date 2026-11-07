# RGB Fusion Remote (Android 13 port)

Re-implementation of GIGABYTE RGB Fusion mobile remote 17.0623.1 with the same protocol and features
(multicast discovery, per-PC and group control, 13 modes, colour/speed/brightness, intelligent modes, schedule, password).

Open in Android Studio (AGP 8.2.2, JDK 17), let it sync, Run. Package: com.rgbfusion.remote
(uninstall the old app first if it conflicts). Phone and PC must be on the same Wi-Fi.
Core protocol logic (app/src/main/java/.../core) is plain Java; self-test:
  javac -d out core/*.java ../../../test/java/com/gigabyte/rgbfusion/core/CoreSelfTest.java
  java -cp out com.gigabyte.rgbfusion.core.CoreSelfTest
