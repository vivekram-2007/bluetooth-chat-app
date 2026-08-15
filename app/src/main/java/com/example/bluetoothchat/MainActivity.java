package com.example.bluetoothchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TYPING_MARKER = "%%TYPING%%";

    private static final int COLOR_ME = Color.parseColor("#4FC3F7");
    private static final int COLOR_THEM = Color.parseColor("#81C784");

    private TextView statusText;
    private Button scanButton;
    private ListView deviceList;
    private Button disconnectButton;
    private TextView messageArea;
    private EditText messageInput;
    private Button sendButton;
    private TextView typingIndicator;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private BluetoothServerSocket serverSocket;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private long lastTypingSentTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        scanButton = findViewById(R.id.scanButton);
        deviceList = findViewById(R.id.deviceList);
        disconnectButton = findViewById(R.id.disconnectButton);
        messageArea = findViewById(R.id.messageArea);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        typingIndicator = findViewById(R.id.typingIndicator);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        requestBluetoothPermissions();
        startListening();

        scanButton.setOnClickListener(v -> toggleDeviceList());

        deviceList.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            deviceList.setVisibility(View.GONE);
            connectToDevice(pairedDevicesList.get(position));
        });

        sendButton.setOnClickListener(v -> sendMessage());
        disconnectButton.setOnClickListener(v -> disconnectBluetooth());

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (outputStream != null && now - lastTypingSentTime > 1500) {
                    lastTypingSentTime = now;
                    new Thread(() -> {
                        try {
                            outputStream.write(TYPING_MARKER.getBytes());
                        } catch (IOException e) {
                            // ignore
                        }
                    }).start();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void toggleDeviceList() {
        if (deviceList.getVisibility() == View.VISIBLE) {
            deviceList.setVisibility(View.GONE);
            startListening();
        } else {
            stopListening();
            showPairedDevices();
            deviceList.setVisibility(View.VISIBLE);
        }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void showPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        pairedDevicesList.clear();
        ArrayList<String> deviceNames = new ArrayList<>();

        for (BluetoothDevice device : bondedDevices) {
            pairedDevicesList.add(device);
            deviceNames.add(device.getName() + "\n" + device.getAddress());
        }

        if (deviceNames.isEmpty()) {
            Toast.makeText(this, "No paired devices found. Pair in phone Settings first.", Toast.LENGTH_LONG).show();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        deviceList.setAdapter(adapter);
    }

    private void startListening() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Status: Listening...");

        new Thread(() -> {
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BluetoothChat", APP_UUID);
                BluetoothSocket socket = serverSocket.accept();
                serverSocket.close();
                bluetoothSocket = socket;
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                runOnUiThread(() -> statusText.setText("Status: Connected"));
                listenForMessages();
            } catch (IOException e) {
                runOnUiThread(() -> statusText.setText("Status: Disconnected"));
            }
        }).start();
    }

    private void stopListening() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Status: Connecting...");

        new Thread(() -> {
            try {
                BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID);
                socket.connect();
                bluetoothSocket = socket;
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                runOnUiThread(() -> statusText.setText("Status: Connected"));
                listenForMessages();
            } catch (IOException e) {
                runOnUiThread(() -> statusText.setText("Status: Disconnected"));
            }
        }).start();
    }

    private void listenForMessages() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    String received = new String(buffer, 0, bytes);

                    if (received.equals(TYPING_MARKER)) {
                        runOnUiThread(() -> {
                            typingIndicator.setText("Typing...");
                            typingIndicator.postDelayed(() -> typingIndicator.setText(""), 2000);
                        });
                    } else {
                        runOnUiThread(() -> appendMessage("Them", received, COLOR_THEM));
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> statusText.setText("Status: Disconnected"));
                    break;
                }
            }
        }).start();
    }

    private void sendMessage() {
        String message = messageInput.getText().toString();
        if (message.isEmpty() || outputStream == null) {
            return;
        }

        new Thread(() -> {
            try {
                outputStream.write(message.getBytes());
            } catch (IOException e) {
                runOnUiThread(() -> statusText.setText("Status: Disconnected"));
            }
        }).start();

        runOnUiThread(() -> {
            appendMessage("Me", message, COLOR_ME);
            messageInput.setText("");
        });
    }

    private void appendMessage(String sender, String text, int color) {
        String line = sender + ": " + text + "\n";
        SpannableString spannable = new SpannableString(line);
        int labelEnd = sender.length() + 1; // includes the colon

        spannable.setSpan(new ForegroundColorSpan(color), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        messageArea.append(spannable);
    }

    private void disconnectBluetooth() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            // ignore, socket already closed
        }
        outputStream = null;
        inputStream = null;
        bluetoothSocket = null;
        statusText.setText("Status: Disconnected");
        messageArea.append("--- Disconnected ---\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }
}