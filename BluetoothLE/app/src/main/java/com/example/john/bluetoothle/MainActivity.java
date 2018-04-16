package com.example.john.bluetoothle;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.bluetooth.*;
import android.widget.EditText;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private final static String TAG = "MainActivity";

    // Bluetooth LE
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


    // Sensor API
    private SensorManager mSensorManager;
    private final float[] mAccelerometerReading = new float[3];
    private final float[] mMagnetometerReading = new float[3];

    private final float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];

    public final double RAD_TO_DEGREE = (180.0/Math.PI);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new View.OnClickListener() {
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

        Button btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                EditText edtCmd = (EditText)findViewById(R.id.edtCmd);
                characteristic.setValue(edtCmd.getText().toString());
                Log.d(TAG,"Notification sent to client: " + client.getName().toString());
                bluetoothGattServer.notifyCharacteristicChanged(client,characteristic,false);
            }
        });

        Button btnSensor = findViewById(R.id.btnSensor);
        btnSensor.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


                mSensorManager.registerListener(MainActivity.this,
                        mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_NORMAL);
                mSensorManager.registerListener(MainActivity.this,
                        mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                        SensorManager.SENSOR_DELAY_NORMAL);

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
        characteristic.setValue("");

        Descriptor = new BluetoothGattDescriptor(descUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);

        characteristic.addDescriptor(Descriptor);
        service.addCharacteristic(characteristic);
        bluetoothGattServer.addService(service);


    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG,"Sensor changed.");

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerReading[0] = event.values[0];
            mAccelerometerReading[1] = event.values[1];
            mAccelerometerReading[2] = event.values[2];
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagnetometerReading[0] = event.values[0];
            mMagnetometerReading[1] = event.values[1];
            mMagnetometerReading[2] = event.values[2];
        }

        Log.d(TAG,"Acceleration X: " + mAccelerometerReading[0]);
        Log.d(TAG,"Acceleration Y: " + mAccelerometerReading[1]);
        Log.d(TAG,"Acceleration Z: " + mAccelerometerReading[2]);

        Log.d(TAG,"Magnetic X: " + mMagnetometerReading[0]);
        Log.d(TAG,"Magnetic Y: " + mMagnetometerReading[1]);
        Log.d(TAG,"Magnetic Z: " + mMagnetometerReading[2]);

        // Rotation matrix based on current readings from accelerometer and magnetometer.
        mSensorManager.getRotationMatrix(mRotationMatrix, null,
                mAccelerometerReading, mMagnetometerReading);

        final float[] orientationAngles = new float[3];
        mSensorManager.getOrientation(mRotationMatrix, orientationAngles);

        // Conversion to Degrees
        orientationAngles[0] *= RAD_TO_DEGREE;
        orientationAngles[1] *= RAD_TO_DEGREE;
        orientationAngles[2] *= RAD_TO_DEGREE;


        EditText edtX = (EditText)findViewById(R.id.edtX);
        EditText edtY = (EditText)findViewById(R.id.edtY);
        EditText edtZ = (EditText)findViewById(R.id.edtZ);

        edtX.setText(orientationAngles[0]+"");
        edtY.setText(orientationAngles[1]+"");
        edtZ.setText(orientationAngles[2]+"");

        if (client != null) {
            // Stream Orientation over BLE
            // eulerX
            characteristic.setValue("eulerX=" + orientationAngles[0]);
            Log.d(TAG, "Notification sent to client: " + client.getName().toString());
            bluetoothGattServer.notifyCharacteristicChanged(client, characteristic, false);

            // eulerY
            characteristic.setValue("eulerY=" + orientationAngles[1]);
            Log.d(TAG, "Notification sent to client: " + client.getName().toString());
            bluetoothGattServer.notifyCharacteristicChanged(client, characteristic, false);

            // eulerZ
            characteristic.setValue("eulerZ=" + orientationAngles[2]);
            Log.d(TAG, "Notification sent to client: " + client.getName().toString());
            bluetoothGattServer.notifyCharacteristicChanged(client, characteristic, false);
        }



    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


}
