package com.example.bluetoothchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import java.util.LinkedHashSet;
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
    private ArrayList<BluetoothDevice> discoveredDevicesList = new ArrayList<>();

    private BluetoothServerSocket serverSocket;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private long lastTypingSentTime = 0;
    private boolean permissionsGranted = false;

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !discoveredDevicesList.contains(device)) {
                    discoveredDevicesList.add(device);
                    refreshDeviceListAdapter();
                }
            }
        }
    };

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

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryReceiver, filter);

        requestBluetoothPermissions();

        scanButton.setOnClickListener(v -> toggleDeviceList());

        deviceList.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            deviceList.setVisibility(View.GONE);
            stopDiscovery();
            connectToDevice(discoveredDevicesList.get(position));
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

    private void requestBluetoothPermissions() {
        boolean needsRequest;
        String[] permsToRequest;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needsRequest = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED;
            permsToRequest = new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};
        } else {
            needsRequest = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
            permsToRequest = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        if (needsRequest) {
            ActivityCompat.requestPermissions(this, permsToRequest, PERMISSION_REQUEST_CODE);
        } else {
            permissionsGranted = true;
            startListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && grantResults.length > 0) {
                permissionsGranted = true;
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                startListening();
            } else {
                permissionsGranted = false;
                Toast.makeText(this, "Bluetooth permissions are required for this app to work", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleDeviceList() {
        if (!permissionsGranted) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        if (deviceList.getVisibility() == View.VISIBLE) {
            deviceList.setVisibility(View.GONE);
            stopDiscovery();
            startListening();
        } else {
            stopListening();
            startDiscoveryAndShowDevices();
            deviceList.setVisibility(View.VISIBLE);
        }
    }

    private void startDiscoveryAndShowDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        discoveredDevicesList.clear();

        // Seed the list with already-paired devices first
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            discoveredDevicesList.add(device);
        }
        refreshDeviceListAdapter();

        // Now also actively search for nearby devices (paired or not)
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        boolean started = bluetoothAdapter.startDiscovery();
        if (!started) {
            Toast.makeText(this, "Could not start device search", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void refreshDeviceListAdapter() {
        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice device : discoveredDevicesList) {
            String name = device.getName();
            if (name == null) {
                name = "Unknown device";
            }
            deviceNames.add(name + "\n" + device.getAddress());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        deviceList.setAdapter(adapter);
    }

    private void startListening() {
        if (!permissionsGranted) {
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            statusText.setText("Status: Bluetooth is off");
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
        if (!permissionsGranted) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Status: Connecting...");

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID);
                socket.connect();
                bluetoothSocket = socket;
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                runOnUiThread(() -> statusText.setText("Status: Connected"));
                listenForMessages();
            } catch (IOException e) {
                final String errorMsg = e.getMessage();
                try {
                    if (socket != null) socket.close();
                } catch (IOException closeEx) {
                    // ignore
                }
                runOnUiThread(() -> {
                    statusText.setText("Status: Disconnected");
                    Toast.makeText(MainActivity.this, "Connection failed: " + errorMsg, Toast.LENGTH_LONG).show();
                });
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
        int labelEnd = sender.length() + 1;

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
        startListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            // receiver already unregistered
        }
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