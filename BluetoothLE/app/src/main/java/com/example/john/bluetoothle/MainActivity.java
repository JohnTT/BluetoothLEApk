package com.example.john.bluetoothle;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.bluetooth.*;
import android.widget.Toast;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    public final static int ENABLE_BLUETOOTH_REQUEST = 1;

    private AdvertiseSettings settings;
    private AdvertiseData advertiseData;
    private AdvertiseData scanResponseData;
    private AdvertiseCallback advCallback;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    private BluetoothGattServerCallback srvCallback;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic characteristic;
    private BluetoothGattDescriptor Descriptor;

    private BluetoothDevice client;

    private UUID serviceUUID, charUUID, descUUID;

    private int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                mBluetoothAdapter = mBluetoothManager.getAdapter();

                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST);
                    Log.i(TAG, "Enable Bluetooth Intent Started.");
                }
                else {
                    Log.i(TAG, "Bluetooth already enabled on device.");
                    start();
                }


            }
        });

        Button button2 = findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                counter += 5;
                Log.d(TAG,"counter = " + counter);
                characteristic.setValue("posX="+counter);
                bluetoothGattServer.notifyCharacteristicChanged(client,characteristic,false);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == ENABLE_BLUETOOTH_REQUEST) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Bluetooth enabled by user.");
                start();
            }
        }
    }

    public void start() {
        serviceUUID = UUID.randomUUID();
//        charUUID = UUID.randomUUID();
//        descUUID = UUID.randomUUID();
        charUUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"); // Adafruit TX
        descUUID = UUID.fromString("000002902-0000-1000-8000-00805f9b34fb");

        settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .build();

        advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();

        scanResponseData = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(serviceUUID))
                .setIncludeTxPowerLevel(true)
                .build();

        advCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "BLE advertisement added successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Failed to add BLE advertisement, reason: " + errorCode);
            }
        };

        bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, advCallback);
        Log.i(TAG,"Bluetooth LE advertising started.");

        srvCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);

                if (status == BluetoothProfile.STATE_CONNECTING) {
                    Log.d(TAG, "Connecting: " + device.getName());
                }
                else if (status == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected: " + device.getName());
                }
                else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected: " + device.getName());
                }
                else if (status == BluetoothProfile.STATE_DISCONNECTING) {
                    Log.d(TAG, "Disconnecting: " + device.getName());
                }


            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                super.onServiceAdded(status, service);
                Log.d(TAG,"Service Added.");
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                client = device;
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                        0, characteristic.getValue());
//                Log.d(TAG, "Device tried to read characteristic: " + characteristic.getUuid());
//                Log.d(TAG, "Value: " + characteristic.getValue().toString());

            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                Log.d(TAG,"Characteristic Write Requect.");
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                Log.d(TAG,"Client descriptor write request.");
                Descriptor.getCharacteristic().setValue(descriptor.getValue());
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                        0, null);

            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
                Log.d(TAG,"Descriptor Read Request.");
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE,
                        0, descriptor.getValue());

            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                super.onNotificationSent(device, status);
                Log.d(TAG,"Client notification sent.");
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
                Log.d(TAG,"MTU Changed.");

            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                super.onExecuteWrite(device, requestId, execute);
                Log.d(TAG,"Execute Write.");
            }
        };

        bluetoothGattServer = mBluetoothManager.openGattServer(getApplicationContext(), srvCallback);
        if (bluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }
        bluetoothGattServer.clearServices();
        service = new BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);


        //add a read characteristic.
        characteristic = new BluetoothGattCharacteristic(charUUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        characteristic.setValue(counter + "");

        Descriptor = new BluetoothGattDescriptor(descUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);

        characteristic.addDescriptor(Descriptor);
        service.addCharacteristic(characteristic);
        bluetoothGattServer.addService(service);


    }


}
