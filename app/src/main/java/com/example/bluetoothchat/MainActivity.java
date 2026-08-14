package com.example.bluetoothchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothDevice;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.widget.AdapterView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private TextView statusText;
    private Button scanButton;
    private ListView deviceList;
    private Button listenButton;
    private TextView messageArea;
    private EditText messageInput;
    private Button sendButton;

    private BluetoothAdapter bluetoothAdapter;

    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        scanButton = findViewById(R.id.scanButton);
        deviceList = findViewById(R.id.deviceList);
        listenButton = findViewById(R.id.listenButton);
        messageArea = findViewById(R.id.messageArea);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        requestBluetoothPermissions();
        scanButton.setOnClickListener(v -> showPairedDevices());

        deviceList.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> connectToDevice(pairedDevicesList.get(position)));
        listenButton.setOnClickListener(v -> startListening());

        sendButton.setOnClickListener(v -> sendMessage());
    }
    private void showPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
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

        statusText.setText("Status: Listening...");

        new Thread(() -> {
            try {
                BluetoothServerSocket serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BluetoothChat", APP_UUID);
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

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Status: Connecting...");

        new Thread(() -> {
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(APP_UUID);
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
                    String receivedMessage = new String(buffer, 0, bytes);
                    runOnUiThread(() -> messageArea.append("Them: " + receivedMessage + "\n"));
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
            messageArea.append("Me: " + message + "\n");
            messageInput.setText("");
        });
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
}