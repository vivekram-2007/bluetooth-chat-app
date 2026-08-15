BluetoothChat



A minimal Android app that lets two nearby devices exchange text messages over Bluetooth, with no internet connection or backend server required.



SETUP AND INSTALLATION



1\. Clone the repository: git clone https://github.com/vivekram-2007/bluetooth-chat-app.git

2\. Open the project folder in Android Studio.

3\. Let Gradle sync finish.

4\. Connect an Android phone via USB with Developer Options and USB Debugging enabled, select it in the device dropdown, and click Run.

5\. Repeat on a second phone.

6\. Pair the two phones with each other in advance through Android Settings > Bluetooth (like pairing headphones).



Alternatively, install the pre-built APK (app-debug.apk) directly on both phones without building from source.



PLATFORM AND TECHNOLOGIES



\- Platform: Android

\- Language: Java

\- IDE: Android Studio

\- Minimum SDK: API 24 (Android 7.0)

\- No external libraries - uses Android's built-in Bluetooth APIs only



BLUETOOTH COMMUNICATION APPROACH



The app uses Bluetooth Classic (RFCOMM), not Bluetooth Low Energy, since RFCOMM provides a plain socket-based stream well suited to two-way text messaging.



\- One phone acts as the server, listening for an incoming connection via BluetoothServerSocket.listenUsingInsecureRfcommWithServiceRecord() with a fixed UUID.

\- The other phone acts as the client, connecting via BluetoothDevice.createInsecureRfcommSocketToServiceRecord() using the same UUID.

\- The app auto-starts listening as soon as it opens. Tapping "Devices" pauses listening and shows the list of paired devices to connect to instead; closing that list without connecting resumes listening automatically.

\- Once connected, both sides get an InputStream/OutputStream over the socket. Sending writes bytes directly; a background thread continuously reads incoming bytes and displays them.

\- All blocking calls (accept(), connect(), read()) run on background threads, since blocking the main thread would freeze the UI and crash the app.

\- A lightweight typing indicator is implemented by sending a small reserved marker string over the same socket, detected and filtered out on the receiving side before being shown as a normal message.



KNOWN LIMITATIONS



\- Only supports one-to-one connections, not group chats (out of scope per the assignment).

\- No message history - messages are not stored and are lost when the app closes or a new connection starts (also out of scope).

\- No encryption on top of Bluetooth's own security.

\- Devices must already be paired via Android Settings before they'll appear in the app's device list; the app does not perform live discovery of unpaired devices.

\- If Bluetooth is turned off, the app shows a prompt but does not turn it on automatically.



AI TOOLS USED



Claude was used throughout development to plan the project structure, write and debug the Java/XML code, diagnose Android Studio and Gradle project-generation issues, explain Bluetooth Classic vs BLE tradeoffs, and review the final code against the assignment brief. All code was reviewed, tested on two physical devices, and understood before submission.



HARDEST PROBLEM ENCOUNTERED



The trickiest bug was a mismatch between the server and client socket types: the server used listenUsingInsecureRfcommWithServiceRecord() (insecure) while the client initially used createRfcommSocketToServiceRecord() (secure). Because these must match, the connection attempt silently failed with no useful error message. The fix was changing the client to use createInsecureRfcommSocketToServiceRecord() to match the server. This was a good reminder that Bluetooth socket types need to correspond exactly on both ends of the connection, and that blocking socket calls always need to run off the main thread to avoid freezing the app.

